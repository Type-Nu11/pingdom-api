package com.typenull.pingdom.consultation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.consultation.infrastructure.gemini.GeminiProperties;
import com.typenull.pingdom.consultation.infrastructure.persistence.VoiceAiRetentionRepository;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers
class VoiceAiRetentionPostgreSqlIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 0);
    private static final LocalDateTime CUTOFF = NOW.minusDays(14);

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private VoiceAiRetentionRepository repository;

    @BeforeEach
    void setup() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        repository = new VoiceAiRetentionRepository(jdbc);
        jdbc.execute("DROP TABLE IF EXISTS voice_ai_replay, voice_ai_session, users CASCADE");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V134__create_voice_ai_session.sql"),
                new ClassPathResource("db/migration/V136__add_voice_ai_replay_processing_lease.sql"),
                new ClassPathResource("db/migration/V146__add_voice_ai_retention.sql")
        ).execute(dataSource);
        jdbc.update("INSERT INTO users (id) VALUES (1)");
    }

    @Test
    void deletesFourteenDayOldCompletedReplayInActiveSessionButKeepsParentAndRecentResult() {
        session("active", NOW.plusMinutes(5), null);
        replay("active", "COMPLETED", CUTOFF, null);
        replay("active", "COMPLETED", CUTOFF.plusSeconds(1), null);

        var result = cleanup(10);

        assertThat(result.deletedReplays()).isEqualTo(1);
        assertThat(result.deletedSessions()).isZero();
        assertThat(count("voice_ai_replay")).isEqualTo(1);
        assertThat(count("voice_ai_session")).isEqualTo(1);
    }

    @Test
    void retainsProcessingUntilBothParentAndRecordedLeaseAreFourteenDaysOld() {
        session("lease-exact", NOW, CUTOFF);
        replay("lease-exact", "PROCESSING", null, CUTOFF);
        session("lease-recent", NOW, CUTOFF);
        replay("lease-recent", "PROCESSING", null, CUTOFF.plusSeconds(1));
        session("lease-valid", NOW, CUTOFF);
        replay("lease-valid", "PROCESSING", null, NOW.plusMinutes(1));
        session("parent-recent", NOW, CUTOFF.plusSeconds(1));
        replay("parent-recent", "PROCESSING", null, CUTOFF.minusDays(1));

        var result = cleanup(10);

        assertThat(result.deletedReplays()).isEqualTo(1);
        assertThat(result.deletedSessions()).isEqualTo(1);
        assertThat(count("voice_ai_session")).isEqualTo(3);
    }

    @Test
    void limitsChildRowsAndDeletesParentOnlyAfterFinalChunk() {
        session("large", NOW, CUTOFF.minusDays(1));
        for (int i = 0; i < 5; i++) replay("large", "COMPLETED", CUTOFF.minusDays(1), null);

        assertThat(cleanup(2).deletedReplays()).isEqualTo(2);
        assertThat(count("voice_ai_replay")).isEqualTo(3);
        assertThat(count("voice_ai_session")).isEqualTo(1);
        assertThat(cleanup(2).deletedReplays()).isEqualTo(2);
        var last = cleanup(2);
        assertThat(last.deletedReplays()).isEqualTo(1);
        assertThat(last.deletedSessions()).isEqualTo(1);
    }

    @Test
    void initializesUnknownTimestampsOnceWithoutMovingThemOnNextRun() {
        session("legacy", NOW, CUTOFF.minusDays(2));
        replay("legacy", "COMPLETED", null, null);
        replay("legacy", "PROCESSING", null, null);

        var first = cleanup(2);
        assertThat(first.initializedTimestamps()).isEqualTo(2);
        assertThat(first.deletedReplays()).isZero();
        assertThat(cleanup(2).hasProgress()).isFalse();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM voice_ai_replay WHERE status='COMPLETED'", LocalDateTime.class))
                .isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT processing_lease_until FROM voice_ai_replay WHERE status='PROCESSING'", LocalDateTime.class))
                .isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void skipsParentLockedByRequestInsteadOfDeletingItsResult() throws Exception {
        session("locked", NOW.plusMinutes(1), null);
        replay("locked", "COMPLETED", CUTOFF, null);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT session_id FROM voice_ai_session WHERE session_id='locked' FOR UPDATE")) {
                statement.executeQuery().close();
                assertThat(cleanup(10).hasProgress()).isFalse();
                assertThat(count("voice_ai_replay")).isEqualTo(1);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void rechecksChildTimeAfterTakingParentLock() {
        session("changed", NOW.plusMinutes(1), null);
        replay("changed", "COMPLETED", CUTOFF, null);
        transactions.executeWithoutResult(status -> {
            assertThat(repository.lockEligibleSessions(CUTOFF, 10)).containsExactly("changed");
            // 후보 조회 결과만 믿지 않고 삭제 statement의 현재 시각 조건을 확인합니다.
            jdbc.update("UPDATE voice_ai_replay SET completed_at=? WHERE session_id='changed'", NOW);
            assertThat(repository.deleteReplayBatch("changed", CUTOFF, 10)).isZero();
        });
        assertThat(count("voice_ai_replay")).isEqualTo(1);
    }

    private VoiceAiRetentionService.CleanupResult cleanup(int rows) {
        GeminiProperties gemini = mock(GeminiProperties.class);
        when(gemini.connectTimeout()).thenReturn(Duration.ofSeconds(2));
        when(gemini.readTimeout()).thenReturn(Duration.ofSeconds(5));
        var service = new VoiceAiRetentionService(repository, new VoiceAiRetentionProperties(10, rows, 2),
                gemini,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        return transactions.execute(status -> service.cleanupBatch());
    }

    private void session(String id, LocalDateTime expiresAt, LocalDateTime closedAt) {
        jdbc.update("INSERT INTO voice_ai_session (session_id,user_id,status,expires_at,closed_at) VALUES (?,1,?,?,?)",
                id, closedAt == null ? "ACTIVE" : "CLOSED", expiresAt, closedAt);
    }

    private void replay(String sessionId, String status, LocalDateTime completedAt, LocalDateTime leaseUntil) {
        jdbc.update("""
                INSERT INTO voice_ai_replay
                (session_id,request_id,payload_hash,envelope,status,processing_token,processing_started_at,
                 processing_lease_until,created_at,completed_at)
                VALUES (?,?,'hash','{}',?,'token',?,?,?,?)
                """, sessionId, UUID.randomUUID().toString(), status,
                NOW.minusDays(30), leaseUntil, NOW.minusDays(30), completedAt);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
