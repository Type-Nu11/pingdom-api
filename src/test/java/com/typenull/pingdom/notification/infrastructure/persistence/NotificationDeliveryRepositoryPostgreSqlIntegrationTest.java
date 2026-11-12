package com.typenull.pingdom.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.application.service.auth.AuthService;
import com.typenull.pingdom.identity.application.service.auth.AuthEmailDispatchPolicy;
import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.outbox.EmailVerificationOutboxPayload;
import com.typenull.pingdom.notification.outbox.PasswordResetOutboxPayload;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class NotificationDeliveryRepositoryPostgreSqlIntegrationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T00:00:00.123456789Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock authEmailClock() { return CLOCK; }
    }

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * 알림 전송 이력의 선택적 날짜 조건 쿼리를 실제 PostgreSQL에서 실행하도록 접속 정보를 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private NotificationDeliveryRepository notificationDeliveryRepository;

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AuthService authService;
    @Autowired private AuthEmailDispatchPolicy dispatchPolicy;
    @Autowired private UserRepository users;
    @Autowired private PasswordResetTokenRepository tokens;
    @Autowired private OutboxEventRepository events;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EmailVerificationOutboxHandler verificationHandler;
    @MockBean private EmailSender emailSender;

    /**
     * 전송 이력 필터 결과를 독립적으로 비교하도록 기존 이력을 제거.
     */
    @BeforeEach
    void cleanDatabase() {
        reset(emailSender);
        notificationDeliveryRepository.deleteAllInBatch();
        events.deleteAllInBatch();
        tokens.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void preservesSuccessfulEmailAgainstLateFailureAndStaleResult() {
        upsertEmail("same-email", "RETRY_SCHEDULED", null);
        upsertEmail("same-email", "SUCCEEDED", null);
        upsertEmail("same-email", "RETRY_SCHEDULED", null);
        upsertEmail("same-email", "FAILED", "EMAIL_REQUEST_STALE");

        NotificationDelivery result = email("same-email");
        assertThat(result.getStatus()).isEqualTo(NotificationDeliveryStatus.SUCCEEDED);
        assertThat(result.getProviderMessageId()).isEqualTo("message-id");
        assertThat(result.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void preservesStaleTerminationAndFinalizesRetryAtLimit() {
        upsertEmail("stale-email", "FAILED", "EMAIL_REQUEST_STALE");
        upsertEmail("stale-email", "RETRY_SCHEDULED", null);
        assertThat(email("stale-email").getErrorCode()).isEqualTo("EMAIL_REQUEST_STALE");
        assertThat(email("stale-email").getAttemptCount()).isEqualTo(1);
        // 이미 진행 중이던 실제 발송의 성공 결과는 stale보다 강한 근거다.
        upsertEmail("stale-email", "SUCCEEDED", null);
        assertThat(email("stale-email").getStatus()).isEqualTo(NotificationDeliveryStatus.SUCCEEDED);
        upsertEmail("retry-email", "RETRY_SCHEDULED", null);
        upsertEmail("retry-email", "RETRY_SCHEDULED", null);
        assertThat(email("retry-email").getStatus()).isEqualTo(NotificationDeliveryStatus.FINAL_FAILED);
        assertThat(email("retry-email").getAttemptCount()).isEqualTo(2);
    }

    @Test
    void handlesConcurrentFirstSuccessAndFailureWithoutDuplicateRows() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> success = executor.submit(() -> concurrentUpsert(ready, start, "SUCCEEDED"));
            Future<?> failure = executor.submit(() -> concurrentUpsert(ready, start, "RETRY_SCHEDULED"));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            success.get(10, TimeUnit.SECONDS);
            failure.get(10, TimeUnit.SECONDS);
            assertThat(notificationDeliveryRepository.count()).isEqualTo(1);
            assertThat(email("concurrent-email").getStatus()).isEqualTo(NotificationDeliveryStatus.SUCCEEDED);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void producerExpirySurvivesPostgreSqlRoundTripAndRemainsDispatchable() throws Exception {
        authService.signup(new SignupRequest("precision-user", "precision@example.com", "password123", 1998, null, "ko", "KR"));
        authService.requestPasswordReset(new PasswordResetRequest("precision@example.com"));
        User user = users.findByUsername("precision-user").orElseThrow();
        OutboxEvent verificationEvent = events.findAll().stream()
                .filter(event -> event.getEventType() == OutboxEventType.EMAIL_VERIFICATION_REQUESTED).findFirst().orElseThrow();
        OutboxEvent resetEvent = events.findAll().stream()
                .filter(event -> event.getEventType() == OutboxEventType.PASSWORD_RESET_REQUESTED).findFirst().orElseThrow();
        EmailVerificationOutboxPayload verification = objectMapper.readValue(verificationEvent.getPayload(), EmailVerificationOutboxPayload.class);
        PasswordResetOutboxPayload reset = objectMapper.readValue(resetEvent.getPayload(), PasswordResetOutboxPayload.class);
        assertThat(verification.expiresAt().getNano() % 1000).isZero();
        assertThat(verification.expiresAt()).isEqualTo(user.getEmailVerificationExpiresAt());
        assertThat(reset.expiresAt().getNano() % 1000).isZero();
        assertThat(reset.expiresAt()).isEqualTo(tokens.findByTokenHash(hash(reset.resetToken())).orElseThrow().getExpiresAt());
        assertThat(dispatchPolicy.canSendVerification(user.getId(), verification.recipientEmail(), verification.verificationCode(), verification.expiresAt())).isTrue();
        assertThat(dispatchPolicy.canSendPasswordReset(user.getId(), reset.recipientEmail(), reset.resetToken(), reset.expiresAt())).isTrue();
    }

    @Test
    void reusesCommittedDeliveryWhileOutboxCompletionIsMissing() throws Exception {
        authService.signup(new SignupRequest("replay-user", "replay@example.com", "password123", 1998, null, "ko", "KR"));
        OutboxEvent event = events.findAll().getFirst();
        EmailVerificationOutboxPayload payload = objectMapper.readValue(event.getPayload(), EmailVerificationOutboxPayload.class);
        when(emailSender.sendVerificationEmail(payload.recipientEmail(), payload.verificationCode())).thenReturn(EmailSendResult.sent("sent-once"));

        verificationHandler.handle(event.getEventId(), event.getPayload());
        // delivery는 별도 커밋되었지만 Outbox 완료 기록은 없는 장애 경계를 재현한다.
        assertThat(events.findById(event.getEventId()).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        verificationHandler.handle(event.getEventId(), event.getPayload());

        verify(emailSender).sendVerificationEmail(payload.recipientEmail(), payload.verificationCode());
        assertThat(notificationDeliveryRepository.findAll()).singleElement()
                .satisfies(delivery -> {
                    assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryStatus.SUCCEEDED);
                    assertThat(delivery.getAttemptCount()).isEqualTo(1);
                    assertThat(delivery.getProviderMessageId()).isEqualTo("sent-once");
                });
    }

    @Test
    void acceptsIdentifiedLegacyResetWithActualDatabaseMicrosecondRounding() throws Exception {
        authService.signup(new SignupRequest("legacy-user", "legacy@example.com", "password123", 1998, null, "ko", "KR"));
        User user = users.findByUsername("legacy-user").orElseThrow();
        for (int nanos : new int[] {123456499, 123456500, 999999500}) {
            String raw = "legacy-token-" + nanos;
            LocalDateTime originalExpiry = LocalDateTime.now(CLOCK).plusMinutes(30).withNano(nanos);
            tokens.saveAndFlush(PasswordResetToken.create(user, hash(raw), originalExpiry, LocalDateTime.now(CLOCK)));
            LocalDateTime storedExpiry = tokens.findByTokenHash(hash(raw)).orElseThrow().getExpiresAt();
            LocalDateTime expected = nanos % 1000 >= 500
                    ? originalExpiry.plusNanos(1000 - nanos % 1000) : originalExpiry.minusNanos(nanos % 1000);
            assertThat(storedExpiry).isEqualTo(expected);
            assertThat(dispatchPolicy.canSendPasswordReset(user.getId(), user.getEmail(), raw, originalExpiry)).isTrue();
            assertThat(dispatchPolicy.canSendPasswordReset(user.getId(), user.getEmail(), raw, originalExpiry.plusNanos(1000))).isFalse();
        }
    }

    private void concurrentUpsert(CountDownLatch ready, CountDownLatch start, String status) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("동시 시작 대기 시간 초과");
            upsertEmail("concurrent-email", status, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 기록 중단", exception);
        }
    }

    private void upsertEmail(String eventId, String status, String errorCode) {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> notificationDeliveryRepository.upsertEmailDelivery(
                status, 1L, eventId, "EMAIL_VERIFICATION_REQUESTED", "email-hash",
                "SUCCEEDED".equals(status) ? "message-id" : null, null, errorCode, null,
                "RETRY_SCHEDULED".equals(status), 2, NOW));
    }

    private NotificationDelivery email(String eventId) {
        return notificationDeliveryRepository.findDeliveryRecord(eventId, NotificationDeliveryChannel.EMAIL, "email-hash").orElseThrow();
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 실패 이력 조회에서 기간 조건 없음·시작만 지정·종료만 지정한 결과와 최신순 정렬을 PostgreSQL에서 검증.
     */
    @Test
    void filtersDeliveriesByOptionalPeriod() {
        NotificationDelivery older = delivery("event-older", NOW.minusDays(10));
        NotificationDelivery newer = delivery("event-newer", NOW.minusDays(2));
        notificationDeliveryRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(NotificationDelivery::getOutboxEventId)
                .containsExactly("event-newer", "event-older");

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(NotificationDelivery::getOutboxEventId)
                .containsExactly("event-newer");

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(NotificationDelivery::getOutboxEventId)
                .containsExactly("event-older");
    }

    /**
     * 실패 상태를 고정하고 선택적 시작·종료 조건으로 생성 시각·ID 내림차순 첫 페이지를 조회.
     */
    private Page<NotificationDelivery> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return notificationDeliveryRepository.findByFilters(
                null,
                null,
                NotificationDeliveryStatus.FAILED,
                null,
                null,
                hasFrom,
                from,
                hasTo,
                to,
                PageRequest.of(0, 20, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                ))
        );
    }

    /**
     * 지정 이벤트 ID와 생성 시각을 가진 재시도 불가 FCM 실패 이력을 만들어 날짜 필터 대상을 준비.
     */
    private NotificationDelivery delivery(String outboxEventId, LocalDateTime createdAt) {
        NotificationDelivery delivery = NotificationDelivery.create(
                NotificationDeliveryChannel.FCM,
                10L,
                100L,
                "NEW_LIKE",
                outboxEventId,
                "MAP_IMAGE_LIKED",
                "recipient-hash-" + outboxEventId,
                createdAt
        );
        delivery.recordResult(
                NotificationDeliveryStatus.FAILED,
                10L,
                100L,
                "NEW_LIKE",
                "MAP_IMAGE_LIKED",
                null,
                null,
                "FCM_SEND_FAILED",
                "failed",
                false,
                1,
                createdAt
        );
        return delivery;
    }
}
