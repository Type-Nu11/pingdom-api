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

    @BeforeEach
    void setUp() {
        eventRepository.deleteAllInBatch();
        OUTBOX_CLOCK.set(INITIAL_TIME);
        reset(emailSender, notificationDeliveryRecorder);
    }

    @Test
    void publishedEventPreservesContractAndCompletesThroughRegisteredHandler() {
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

    @Test
    void transientHandlerFailureIsRetriedAfterBackoffAndThenCompletes() {
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

    @Test
    void repeatedHandlerFailureStopsAtMaximumAttemptsWithFinalFailureState() {
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

    @Test
    void staleClaimIsRecoveredIntoRetryFlowWithBackoff() {
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

    private String publishEmail(String suffix) {
        return eventPublisher.publish(
                OutboxEventContractFixture.deduplicationKey(suffix),
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                OutboxEventContractFixture.emailVerificationPayload(),
                OutboxEventContractFixture.AGGREGATE_TYPE,
                OutboxEventContractFixture.AGGREGATE_ID
        );
    }

    private void processClaimedEvent(String eventId) {
        List<String> claimedEventIds = claimService.claimReadyEvents();
        assertThat(claimedEventIds).containsExactly(eventId);
        processor.process(eventId);
    }

    private OutboxEvent event(String eventId) {
        return eventRepository.findById(eventId).orElseThrow();
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(OUTBOX_CLOCK.instant(), ZoneOffset.UTC);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OutboxContractTestConfiguration {

        @Bean
        @Primary
        Clock contractOutboxClock() {
            return OUTBOX_CLOCK;
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
