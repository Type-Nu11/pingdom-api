package com.typenull.pingdom.shared.outbox.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.application.OutboxClaim;
import com.typenull.pingdom.shared.outbox.application.OutboxEventClaimService;
import com.typenull.pingdom.shared.outbox.application.OutboxEventStateService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
class OutboxEventRepositoryPostgreSqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * PostGIS 컨테이너의 JDBC 주소·계정·드라이버를 Spring 데이터소스에 등록.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired private OutboxEventClaimService claimService;
    @Autowired private OutboxEventStateService stateService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    /**
     * 조회 테스트 전에 Outbox 이벤트를 일괄 삭제해 생성 시각 정렬과 기간 결과를 격리.
     */
    @BeforeEach
    void cleanDatabase() {
        outboxEventRepository.deleteAllInBatch();
    }

    /**
     * 실패 이벤트 2건에 대해 기간 없음·시작만·종료만 조건을 PostgreSQL에서 실행해 최신순/해당 기간 결과를 검증.
     */
    @Test
    void queriesOptionalOutboxPeriods() {
        OutboxEvent older = failedEvent("older", NOW.minusDays(10));
        OutboxEvent newer = failedEvent("newer", NOW.minusDays(2));
        outboxEventRepository.saveAllAndFlush(List.of(older, newer));

        assertThat(findByPeriod(false, null, false, null).getContent())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly("newer", "older");

        assertThat(findByPeriod(true, NOW.minusDays(5), false, null).getContent())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly("newer");

        assertThat(findByPeriod(false, null, true, NOW.minusDays(5)).getContent())
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly("older");
    }

    @Test
    void oldTokenCannotChangeNewClaimAfterRecovery() {
        String id = savePending();
        OutboxClaim first = claimService.claimReadyEvents(1).getFirst();
        expireLease(id);
        assertThat(claimService.recoverStaleEvents()).isEqualTo(1);
        jdbcTemplate.update("""
                UPDATE outbox_event SET next_attempt_at =
                    (clock_timestamp() AT TIME ZONE 'UTC') - INTERVAL '1 second'
                WHERE event_id = ?
                """, id);
        OutboxClaim second = claimService.claimReadyEvents(1).getFirst();
        assertThat(second.claimToken()).isNotEqualTo(first.claimToken());
        OutboxEvent before = outboxEventRepository.findById(id).orElseThrow();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThat(transaction.<Integer>execute(status ->
                outboxEventRepository.completeClaim(id, first.claimToken()))).isZero();
        assertThat(transaction.<Integer>execute(status ->
                outboxEventRepository.failClaim(id, first.claimToken(), 5, 10000L, "late"))).isZero();
        assertThat(transaction.<Integer>execute(status ->
                outboxEventRepository.renewClaim(id, first.claimToken(), 300000L, 1800000L))).isZero();
        assertThat(transaction.<Integer>execute(status ->
                outboxEventRepository.releaseClaim(id, first.claimToken()))).isZero();

        OutboxEvent unchanged = outboxEventRepository.findById(id).orElseThrow();
        assertThat(unchanged.getClaimToken()).isEqualTo(second.claimToken());
        assertThat(unchanged.getAttemptCount()).isEqualTo(before.getAttemptCount());
        assertThat(unchanged.getNextAttemptAt()).isEqualTo(before.getNextAttemptAt());
        assertThat(unchanged.getVersion()).isEqualTo(before.getVersion());
        assertThat(stateService.markSucceeded(second)).isTrue();
    }

    @Test
    void expiredLeaseCannotBeRevivedBeforeReclaim() {
        String id = savePending();
        OutboxClaim claim = claimService.claimReadyEvents(1).getFirst();
        expireLease(id);

        assertThat(stateService.renewClaim(claim)).isFalse();
        assertThat(stateService.markSucceeded(claim)).isFalse();
        assertThat(stateService.markFailed(claim, new IllegalStateException("late"))).isNull();
        assertThat(claimService.recoverStaleEvents()).isEqualTo(1);
    }

    @Test
    void activeLeaseIgnoresOldStartTimeAndRenewalBumpsVersion() {
        String id = savePending();
        OutboxClaim claim = claimService.claimReadyEvents(1).getFirst();
        jdbcTemplate.update("""
                UPDATE outbox_event SET processing_started_at =
                    (clock_timestamp() AT TIME ZONE 'UTC') - INTERVAL '10 minutes'
                WHERE event_id = ?
                """, id);
        long version = outboxEventRepository.findById(id).orElseThrow().getVersion();

        assertThat(claimService.recoverStaleEvents()).isZero();
        assertThat(stateService.renewClaim(claim)).isTrue();
        assertThat(outboxEventRepository.findById(id).orElseThrow().getVersion()).isGreaterThan(version);
        assertThat(stateService.releaseClaim(claim)).isTrue();
        OutboxEvent released = outboxEventRepository.findById(id).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(OutboxEventStatus.RETRY);
        assertThat(released.getAttemptCount()).isZero();
        assertThat(released.getClaimToken()).isNull();
    }

    @Test
    void legacyProcessingWithoutLeaseIsRecovered() {
        String id = savePending();
        jdbcTemplate.update("UPDATE outbox_event SET status = 'PROCESSING' WHERE event_id = ?", id);

        assertThat(claimService.recoverStaleEvents()).isEqualTo(1);
        assertThat(outboxEventRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.RETRY);
    }

    private String savePending() {
        OutboxEvent event = OutboxEvent.create("lease-test", OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}", "USER", "1", outboxEventRepository.databaseNow().minusSeconds(1));
        return outboxEventRepository.saveAndFlush(event).getEventId();
    }

    private void expireLease(String id) {
        jdbcTemplate.update("""
                UPDATE outbox_event SET lease_until =
                    (clock_timestamp() AT TIME ZONE 'UTC') - INTERVAL '1 second'
                WHERE event_id = ?
                """, id);
    }

    /**
     * FAILED 상태와 주어진 생성 기간 플래그를 적용하고 생성 시각·이벤트 ID 내림차순 첫 20건을 조회.
     */
    private Page<OutboxEvent> findByPeriod(
            boolean hasFrom,
            LocalDateTime from,
            boolean hasTo,
            LocalDateTime to
    ) {
        return outboxEventRepository.findByFilters(
                OutboxEventStatus.FAILED,
                null,
                null,
                null,
                hasFrom,
                from,
                hasTo,
                to,
                PageRequest.of(0, 20, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("eventId")
                ))
        );
    }

    /**
     * 주어진 집계와 생성 시각의 이메일 이벤트를 한 번 선점·실패시켜 최대 시도 1회의 FAILED 입력을 생성.
     */
    private OutboxEvent failedEvent(String aggregateId, LocalDateTime createdAt) {
        OutboxEvent event = OutboxEvent.create(
                "EMAIL_VERIFICATION:" + aggregateId,
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                "{}",
                "USER",
                aggregateId,
                createdAt
        );
        event.claim("test-token", createdAt.plusMinutes(1), createdAt.plusMinutes(1).plusMinutes(5));
        event.fail(createdAt.plusMinutes(2), 1, createdAt.plusMinutes(2), "provider unavailable");
        return event;
    }
}
