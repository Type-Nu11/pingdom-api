package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.notification.application.service.NotificationDeliveryRecorder;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class OutboxEventContractIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-07-27T03:00:00Z");
    private static final MutableClock OUTBOX_CLOCK = new MutableClock(INITIAL_TIME);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * PostGIS 컨테이너의 JDBC 접속값을 등록해 실제 Outbox 저장·선점·상태 전이를 검증한다.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @MockBean private EmailSender emailSender;
    @MockBean private NotificationDeliveryRecorder notificationDeliveryRecorder;

    @Autowired private OutboxEventPublisher eventPublisher;
    @Autowired private OutboxEventClaimService claimService;
    @Autowired private OutboxEventProcessor processor;
    @Autowired private OutboxEventRepository eventRepository;
    @Autowired private OutboxProperties outboxProperties;

    /**
     * 이벤트 테이블을 비우고 Clock과 이메일/전달 기록 대역을 초기화해 각 재시도 시나리오를 격리한다.
     */
    @BeforeEach
    void setUp() {
        eventRepository.deleteAllInBatch();
        OUTBOX_CLOCK.set(INITIAL_TIME);
        reset(emailSender, notificationDeliveryRecorder);
    }

    /**
     * 동일 키 중복 발행은 생략하고 PENDING payload·집계 정보를 유지한 이벤트가 선점 후 등록 핸들러를 거쳐 SUCCEEDED가 되는지 검증한다.
     * 실제 이메일 전송은 대역이며 처리 시각·시도 0회와 이메일 인자 전달을 확인한다.
     */
    @Test
    void publishesAndCompletesRegisteredEvent() {
        when(emailSender.sendVerificationEmail(
                OutboxEventContractFixture.RECIPIENT_EMAIL,
                OutboxEventContractFixture.VERIFICATION_CODE
        )).thenReturn(EmailSendResult.sent("provider-message-id"));

        String eventId = publishEmail("success");
        String duplicateEventId = eventPublisher.publish(
                OutboxEventContractFixture.deduplicationKey("success"),
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                OutboxEventContractFixture.emailVerificationPayload(),
                OutboxEventContractFixture.AGGREGATE_TYPE,
                OutboxEventContractFixture.AGGREGATE_ID
        );

        assertThat(duplicateEventId).as("동일 deduplication key는 새 이벤트를 만들면 안 된다").isNull();
        OutboxEvent pending = event(eventId);
        assertThat(pending.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(pending.getAggregateType()).isEqualTo(OutboxEventContractFixture.AGGREGATE_TYPE);
        assertThat(pending.getAggregateId()).isEqualTo(OutboxEventContractFixture.AGGREGATE_ID);
        assertThat(pending.getPayload()).contains(OutboxEventContractFixture.RECIPIENT_EMAIL);

        assertThat(claimService.claimReadyEvents()).containsExactly(eventId);
        processor.process(eventId);

        OutboxEvent succeeded = event(eventId);
        assertThat(succeeded.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
        assertThat(succeeded.getAttemptCount()).isZero();
        assertThat(succeeded.getProcessedAt()).isEqualTo(now());
        verify(emailSender).sendVerificationEmail(
                OutboxEventContractFixture.RECIPIENT_EMAIL,
                OutboxEventContractFixture.VERIFICATION_CODE
        );
    }

    /**
     * 이메일 처리 첫 실패가 RETRY·시도 1회·오류·10초 backoff를 저장하고 Clock 전진 후 재처리가 SUCCEEDED가 되는지 검증한다.
     */
    @Test
    void retriesTransientHandlerFailure() {
        doThrow(new IllegalStateException("provider temporarily unavailable"))
                .doReturn(EmailSendResult.sent("provider-message-id"))
                .when(emailSender)
                .sendVerificationEmail(
                        OutboxEventContractFixture.RECIPIENT_EMAIL,
                        OutboxEventContractFixture.VERIFICATION_CODE
                );
        String eventId = publishEmail("retry");

        processClaimedEvent(eventId);

        OutboxEvent retry = event(eventId);
        assertThat(retry.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(retry.getAttemptCount()).isEqualTo(1);
        assertThat(retry.getLastError()).contains("provider temporarily unavailable");
        assertThat(retry.getNextAttemptAt()).isEqualTo(now().plusSeconds(10));

        OUTBOX_CLOCK.advanceSeconds(10);
        processClaimedEvent(eventId);

        OutboxEvent succeeded = event(eventId);
        assertThat(succeeded.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
        assertThat(succeeded.getAttemptCount()).isEqualTo(1);
    }

    /**
     * 계속 실패하는 핸들러를 설정된 최대 횟수까지 재선점·처리하여 각 시도 수와 최종 FAILED·오류 기록을 검증한다.
     */
    @Test
    void failsAtMaximumHandlerAttempts() {
        doThrow(new IllegalStateException("provider unavailable"))
                .when(emailSender)
                .sendVerificationEmail(
                        OutboxEventContractFixture.RECIPIENT_EMAIL,
                        OutboxEventContractFixture.VERIFICATION_CODE
                );
        String eventId = publishEmail("max-attempts");

        for (int attempt = 1; attempt <= outboxProperties.maxAttempts(); attempt++) {
            processClaimedEvent(eventId);
            OutboxEvent event = event(eventId);
            assertThat(event.getAttemptCount()).isEqualTo(attempt);
            if (attempt < outboxProperties.maxAttempts()) {
                assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
                OUTBOX_CLOCK.set(event.getNextAttemptAt().atZone(ZoneOffset.UTC).toInstant());
            } else {
                assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
                assertThat(event.getLastError()).contains("provider unavailable");
            }
        }
    }

    /**
     * 선점 후 301초 멈춘 이벤트는 stale 복구 1건·RETRY·시도 1회·선점 시각 제거·10초 backoff로 저장되는지 검증한다.
     */
    @Test
    void recoversStaleClaimWithBackoff() {
        String eventId = publishEmail("stale");
        assertThat(claimService.claimReadyEvents()).containsExactly(eventId);

        OUTBOX_CLOCK.advanceSeconds(301);

        assertThat(claimService.recoverStaleEvents()).isEqualTo(1);

        OutboxEvent recovered = event(eventId);
        assertThat(recovered.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(recovered.getAttemptCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getNextAttemptAt()).isEqualTo(now().plusSeconds(10));
    }

    /**
     * 사례별 중복 키와 공통 이메일 payload·사용자 집계로 실제 Outbox 이벤트를 발행한다.
     */
    private String publishEmail(String suffix) {
        return eventPublisher.publish(
                OutboxEventContractFixture.deduplicationKey(suffix),
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                OutboxEventContractFixture.emailVerificationPayload(),
                OutboxEventContractFixture.AGGREGATE_TYPE,
                OutboxEventContractFixture.AGGREGATE_ID
        );
    }

    /**
     * 준비된 이벤트가 기대 ID 하나로만 선점되는지 확인한 뒤 processor로 전달한다.
     */
    private void processClaimedEvent(String eventId) {
        List<String> claimedEventIds = claimService.claimReadyEvents();
        assertThat(claimedEventIds).containsExactly(eventId);
        processor.process(eventId);
    }

    /**
     * 이벤트를 저장소에서 다시 읽어 서비스 호출 이후 실제 영속 상태를 assertion에 제공한다.
     */
    private OutboxEvent event(String eventId) {
        return eventRepository.findById(eventId).orElseThrow();
    }

    /**
     * 공유 테스트 Clock의 현재 Instant를 Outbox 저장 시간 기준인 UTC LocalDateTime으로 변환한다.
     */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(OUTBOX_CLOCK.instant(), ZoneOffset.UTC);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OutboxContractTestConfiguration {

        /**
         * 공유 변경 가능 Clock을 Primary 빈으로 등록해 발행·선점·상태 서비스가 같은 시간을 사용하게 한다.
         */
        @Bean
        @Primary
        Clock contractOutboxClock() {
            return OUTBOX_CLOCK;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        /**
         * Outbox 시나리오에서 직접 변경할 초기 Instant를 보관한다.
         */
        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        /**
         * Outbox 테스트의 시간대를 UTC로 고정한다.
         */
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        /**
         * UTC 전용 테스트 Clock이므로 요청 시간대와 무관하게 같은 인스턴스를 반환한다.
         */
        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        /**
         * 서비스가 사용하는 현재 테스트 Instant를 반환한다.
         */
        @Override
        public Instant instant() {
            return instant;
        }

        /**
         * 현재 시각을 지정된 다음 재시도 시각이나 초기 시각으로 이동한다.
         */
        private void set(Instant instant) {
            this.instant = instant;
        }

        /**
         * 실제 대기 없이 시각을 전진시켜 backoff와 stale 경계를 재현한다.
         */
        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
