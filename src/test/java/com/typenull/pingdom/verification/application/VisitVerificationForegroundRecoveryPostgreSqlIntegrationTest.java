package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.verification.api.dto.ForegroundVisitVerificationStartRequest;
import com.typenull.pingdom.verification.domain.VisitVerificationSession;
import com.typenull.pingdom.verification.domain.VisitVerificationSessionStatus;
import com.typenull.pingdom.verification.domain.exception.VisitorVerificationException;
import com.typenull.pingdom.verification.infrastructure.VisitVerificationSessionRepository;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import com.typenull.pingdom.verification.infrastructure.VisitVerificationSessionRepository.ForegroundRecoveryCandidate;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
@Import(VisitVerificationForegroundRecoveryPostgreSqlIntegrationTest.SqlConfiguration.class)
class VisitVerificationForegroundRecoveryPostgreSqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");
    private static final LocalDate DATE = LocalDate.ofInstant(NOW, ZoneId.of("Asia/Seoul"));
    private static final List<VisitVerificationSessionStatus> ACTIVE =
            List.of(VisitVerificationSessionStatus.STARTED, VisitVerificationSessionStatus.IN_PROGRESS);

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

    @Autowired private VisitVerificationService service;
    @SpyBean private VisitVerificationSessionRepository sessions;
    @Autowired private UserRepository users;
    @Autowired private MapPlaceRepository places;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private LocationCheckInRepository checkIns;
    @Autowired private VisitVerificationProperties properties;
    @Autowired private VisitVerificationPolicyResolver policyResolver;
    private final AtomicReference<Runnable> afterFirstBatch = new AtomicReference<>();

    @BeforeEach
    void setup() {
        jdbc.execute("TRUNCATE visit_verification_session, map_place, users RESTART IDENTITY CASCADE");
        afterFirstBatch.set(null);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (invocation.getArgument(3) == null) {
                Runnable hook = afterFirstBatch.get();
                if (hook != null) hook.run();
            }
            return result;
        }).when(sessions).findForegroundRecoveryBatch(any(), any(), anyCollection(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"1,0", "20,0", "20,10", "20,19", "100,0", "100,50", "100,99"})
    void boundsSqlAndReadsActualRepeatableReadTransaction(int count, int matchIndex) {
        Fixture fixture = fixture(count, matchIndex);
        AtomicReference<String> isolation = new AtomicReference<>();
        afterFirstBatch.set(() -> isolation.set(jdbc.queryForObject("SHOW transaction_isolation", String.class)));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

        List<String> statements = SqlRecorder.record(() ->
                assertThat(service.startForeground(fixture.userId(), request()).id())
                        .isEqualTo(fixture.sessionIds().get(matchIndex)));

        assertThat(isolation.get()).isEqualTo("repeatable read");
        long recoverySelects = statements.stream().filter(this::isSessionSelect).count();
        assertThat(recoverySelects).isEqualTo(matchIndex / 50 + 2L); // 배치 조회 + 선택한 한 행 잠금
        assertThat(statements.stream().filter(sql -> sql.toLowerCase(Locale.ROOT).contains("map_place"))
                .allMatch(sql -> sql.toLowerCase(Locale.ROOT).contains("left join"))).isTrue();
        assertThat(sessions.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @CsvSource({"1", "20", "100"})
    void scansAllBatchesWithoutMatchInsteadOfApplyingAUserWideCap(int count) {
        Fixture fixture = fixture(count, -1);

        List<String> statements = SqlRecorder.record(() ->
                assertThatThrownBy(() -> service.startForeground(fixture.userId(), request()))
                        .isInstanceOf(VisitorVerificationException.class));

        assertThat(statements.stream().filter(this::isSessionSelect).count()).isEqualTo(count / 50 + 1L);
        assertThat(sessions.count()).isEqualTo(count);
    }

    @Test
    void keepsIdTieOrderAndOriginalCursorAfterConditionalExpiry() {
        Fixture fixture = fixture(3, -1);
        jdbc.update("UPDATE visit_verification_session SET last_verified_at = ?, expires_at = ?",
                java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.executeWithoutResult(status -> {
            List<ForegroundRecoveryCandidate> first = sessions.findForegroundRecoveryBatch(fixture.userId(), DATE,
                    ACTIVE, null, null, PageRequest.of(0, 2));
            assertThat(first).extracting(ForegroundRecoveryCandidate::getId)
                    .containsExactly(fixture.sessionIds().get(2), fixture.sessionIds().get(1));
            Instant cursor = first.getLast().getLastVerifiedAt();
            Long cursorId = first.getLast().getId();

            assertThat(sessions.expireForegroundPrefix(fixture.userId(), DATE,
                    first.stream().map(ForegroundRecoveryCandidate::getId).toList(), ACTIVE,
                    VisitVerificationSessionStatus.EXPIRED, NOW.plusSeconds(1), NOW.minusSeconds(15))).isEqualTo(2);

            assertThat(first.getLast().getLastVerifiedAt()).isEqualTo(cursor);
            assertThat(sessions.findForegroundRecoveryBatch(fixture.userId(), DATE, ACTIVE,
                    cursor, cursorId, PageRequest.of(0, 2))).extracting(ForegroundRecoveryCandidate::getId)
                    .containsExactly(fixture.sessionIds().getFirst());
        });
    }

    @Test
    void concurrentObservationRollsBackVisitedExpiryAndDoesNotCreateNewSession() throws Exception {
        Fixture fixture = fixture(2, 1);
        Long expiredPrefixId = fixture.sessionIds().getFirst();
        jdbc.update("UPDATE visit_verification_session SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), expiredPrefixId);
        Long selectedId = fixture.sessionIds().getLast();

        Throwable failure = raceAfterFirstBatch(fixture, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> sessions.findByIdAndTouristUserIdForUpdate(selectedId, fixture.userId())
                        .orElseThrow().recordObservation(NOW, NOW, 0)));

        assertThat(hasSqlState(failure, "40001")).isTrue();
        assertThat(sessions.count()).isEqualTo(2);
        assertThat(sessions.findById(expiredPrefixId).orElseThrow().getStatus())
                .isEqualTo(VisitVerificationSessionStatus.STARTED);
        assertThat(sessions.findById(selectedId).orElseThrow().getLastVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void observationCannotMoveUnvisitedCandidateAboveCursorAndTriggerNewSession() throws Exception {
        Fixture fixture = fixture(100, 99);
        Long selectedId = fixture.sessionIds().getLast();

        Throwable failure = raceAfterFirstBatch(fixture, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> sessions.findByIdAndTouristUserIdForUpdate(selectedId, fixture.userId())
                        .orElseThrow().recordObservation(NOW, NOW, 0)));

        assertThat(hasSqlState(failure, "40001")).isTrue();
        assertThat(sessions.count()).isEqualTo(100);
        assertThat(sessions.findById(selectedId).orElseThrow().getStatus())
                .isEqualTo(VisitVerificationSessionStatus.IN_PROGRESS);
    }

    @Test
    void expiresGapExceededByOneHundredNanosecondsWithoutFalseConflict() {
        Fixture fixture = fixture(1, 0);
        Long previousId = fixture.sessionIds().getFirst();
        jdbc.update("UPDATE visit_verification_session SET last_verified_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.minusSeconds(15)), previousId);
        jdbc.update("UPDATE map_place SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)");
        VisitVerificationService preciseClockService = new VisitVerificationService(sessions, checkIns, users, places,
                Clock.fixed(NOW.plusNanos(100), ZoneOffset.UTC), properties, policyResolver);
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        Long replacementId = transaction.execute(status ->
                preciseClockService.startForeground(fixture.userId(), request()).id());

        assertThat(replacementId).isNotEqualTo(previousId);
        assertThat(sessions.findById(previousId).orElseThrow().getStatus()).isEqualTo(VisitVerificationSessionStatus.EXPIRED);
        assertThat(sessions.findById(replacementId).orElseThrow().getStatus()).isEqualTo(VisitVerificationSessionStatus.STARTED);
    }

    @Test
    void conditionalExpiryDoesNotOverwriteFreshOrCompletedRows() {
        Fixture fixture = fixture(3, -1);
        Long stale = fixture.sessionIds().getFirst();
        Long fresh = fixture.sessionIds().get(1);
        Long finished = fixture.sessionIds().getLast();
        jdbc.update("UPDATE visit_verification_session SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), stale);
        // PROXIMITY_LOST 역시 완료와 마찬가지로 ACTIVE 갱신 대상에 포함되지 않습니다.
        jdbc.update("UPDATE visit_verification_session SET status = 'PROXIMITY_LOST', expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), finished);

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThat(sessions.expireForegroundPrefix(fixture.userId(), DATE, fixture.sessionIds(), ACTIVE,
                        VisitVerificationSessionStatus.EXPIRED, NOW, NOW.minusSeconds(15))).isEqualTo(1));

        assertThat(sessions.findById(stale).orElseThrow().getStatus()).isEqualTo(VisitVerificationSessionStatus.EXPIRED);
        assertThat(sessions.findById(fresh).orElseThrow().getStatus()).isEqualTo(VisitVerificationSessionStatus.STARTED);
        assertThat(sessions.findById(finished).orElseThrow().getStatus()).isEqualTo(VisitVerificationSessionStatus.PROXIMITY_LOST);
    }

    private Throwable raceAfterFirstBatch(Fixture fixture, Runnable writer) throws Exception {
        CountDownLatch snapshotRead = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        afterFirstBatch.set(() -> {
            snapshotRead.countDown();
            await(writerCommitted);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> recovery = executor.submit(() -> {
                try {
                    service.startForeground(fixture.userId(), request());
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            try {
                assertThat(snapshotRead.await(5, TimeUnit.SECONDS)).isTrue();
                writer.run();
            } finally {
                writerCommitted.countDown();
            }
            return recovery.get(10, TimeUnit.SECONDS);
        } finally {
            writerCommitted.countDown();
            afterFirstBatch.set(null);
            executor.shutdownNow();
        }
    }

    private Fixture fixture(int count, int matchIndex) {
        return new TransactionTemplate(transactions).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            User user = users.saveAndFlush(User.builder().username("foreground-" + suffix)
                    .email("foreground-" + suffix + "@example.invalid").password("unused").birthYear(1995)
                    .language("ko").country("KR").role(UserRole.USER).build());
            List<Long> ids = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                MapPlace place = places.saveAndFlush(MapPlace.builder().name("foreground-" + index)
                        .address("테스트 주소").latitude(index == matchIndex ? 35.1801 : 36.1801)
                        .longitude(128.1078).registrant("test").build());
                Instant lastVerified = NOW.minusMillis(index);
                VisitVerificationSession session = VisitVerificationSession.start(user.getId(), place.getId(), DATE,
                        lastVerified, lastVerified, 0, 1000, Duration.ofSeconds(30), Duration.ofMinutes(5));
                ids.add(sessions.saveAndFlush(session).getId());
            }
            return new Fixture(user.getId(), ids);
        });
    }

    private ForegroundVisitVerificationStartRequest request() {
        return new ForegroundVisitVerificationStartRequest(35.1801, 128.1078, 10d, NOW);
    }

    private boolean isSessionSelect(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        return normalized.startsWith("select") && normalized.contains("visit_verification_session");
    }

    private boolean hasSqlState(Throwable failure, String sqlState) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof SQLException sql && sqlState.equals(sql.getSQLState())) return true;
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("경합 동기화 시간 초과");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private record Fixture(Long userId, List<Long> sessionIds) {}

    @TestConfiguration
    static class SqlConfiguration {
        @Bean
        @Primary
        Clock foregroundClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        HibernatePropertiesCustomizer foregroundSqlRecorder() {
            return properties -> properties.put(AvailableSettings.STATEMENT_INSPECTOR, new SqlRecorder());
        }
    }

    static class SqlRecorder implements StatementInspector {
        private static final ThreadLocal<List<String>> statements = new ThreadLocal<>();

        @Override
        public String inspect(String sql) {
            List<String> current = statements.get();
            if (current != null) current.add(sql);
            return sql;
        }

        static List<String> record(Runnable operation) {
            List<String> captured = new ArrayList<>();
            statements.set(captured);
            try {
                operation.run();
                return captured;
            } finally {
                statements.remove();
            }
        }
    }
}
