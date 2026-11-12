package com.typenull.pingdom.moderation.application.service.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.typenull.pingdom.engagement.application.service.ReportPolicyService;
import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.TrustScoreChangeHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 정책 행 잠금·최초 생성 충돌·점수/이력 트랜잭션을 확인하는 별도 승인 대상 검증. */
@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class TrustScoreBatchPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private TrustScoreBatchService batch;
    @Autowired private TrustScoreRecalculationService recalculation;
    @Autowired private ReportPolicyService reportPolicy;
    @Autowired private ReporterModerationPolicyRepository policies;
    @SpyBean private TrustScoreChangeHistoryRepository histories;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void clean() {
        histories.deleteAllInBatch();
        policies.deleteAllInBatch();
    }

    @Test
    void readsLatestStatisticsAfterWaitingForRealtimePolicyUpdate() throws Exception {
        policies.saveAndFlush(policy(101L));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        Future<TrustScoreRecalculationService.RecalculationResult>[] pending = new Future[1];
        LocalDateTime restriction = LocalDateTime.of(2026, 12, 1, 0, 0);
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                ReporterModerationPolicy policy = policies.findByReporterUserIdForUpdate(101L).orElseThrow();
                policy.recordDeclined("reporter");
                policy.restrictUntil(restriction, "realtime restriction");
                pending[0] = executor.submit(() -> {
                    started.countDown();
                    return recalculation.recalculate(101L);
                });
                await(started);
                assertThatThrownBy(() -> pending[0].get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            });

            assertThat(pending[0].get(5, TimeUnit.SECONDS).changed()).isFalse();
            ReporterModerationPolicy after = policies.findById(101L).orElseThrow();
            assertThat(after.getFalseReportCount()).isEqualTo(4);
            assertThat(after.getDeclinedCount()).isEqualTo(4);
            assertThat(after.getTrustScore()).isEqualTo(25);
            assertThat(after.getRestrictedUntil()).isEqualTo(restriction);
            assertThat(histories.count()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void historyFailureRollsBackCurrentPolicyButPreservesEarlierCommittedPolicy() {
        policies.saveAllAndFlush(List.of(policy(101L), policy(102L)));
        doThrow(new IllegalStateException("history unavailable")).when(histories)
                .save(argThat(history -> history != null && history.getReporterUserId().equals(102L)));

        assertThatThrownBy(batch::recalculate).isInstanceOf(IllegalStateException.class);

        assertThat(policies.findById(101L).orElseThrow().getTrustScore()).isEqualTo(45);
        assertThat(policies.findById(102L).orElseThrow().getTrustScore()).isEqualTo(20);
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test
    void concurrentInitialSubmissionsShareOnePolicyWithoutLosingCounts() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { await(start); reportPolicy.recordSubmitted(101L, "reporter"); });
            var second = executor.submit(() -> { await(start); reportPolicy.recordSubmitted(101L, "reporter"); });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertThat(policies.count()).isEqualTo(1);
            assertThat(policies.findById(101L).orElseThrow().getSubmittedCount()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void scalarIdPagesRespectCursorAndInitialUpperBound() {
        policies.saveAllAndFlush(List.of(policy(101L), policy(103L), policy(105L)));

        assertThat(policies.findMaxReporterUserId()).isEqualTo(105L);
        assertThat(policies.findReporterUserIdsAfter(null, 103L, PageRequest.of(0, 1))).containsExactly(101L);
        assertThat(policies.findReporterUserIdsAfter(101L, 103L, PageRequest.of(0, 10))).containsExactly(103L);
    }

    private ReporterModerationPolicy policy(Long id) {
        return ReporterModerationPolicy.builder().reporterUserId(id).reporterUsername("reporter")
                .submittedCount(5).acceptedCount(1).declinedCount(3).falseReportCount(3).trustScore(20).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동기화 지점 대기 시간 초과");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동기화 대기 중 인터럽트", exception);
        }
    }
}
