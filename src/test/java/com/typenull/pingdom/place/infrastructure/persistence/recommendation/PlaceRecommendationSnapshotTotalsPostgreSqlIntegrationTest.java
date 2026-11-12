package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.ThreadMXBean;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.typenull.pingdom.place.infrastructure.persistence.recommendation."
                + "PlaceRecommendationSnapshotTotalsPostgreSqlIntegrationTest$SqlCapture",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class PlaceRecommendationSnapshotTotalsPostgreSqlIntegrationTest {

    private static final boolean BENCHMARK = Boolean.parseBoolean(System.getenv("PINGDOM_GLOBAL_TOTALS_BENCHMARK"));
    private static final String TABLE = "place_recommendation_snapshot";

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName
            .parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceRecommendationSnapshotRepository snapshots;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM place_recommendation_snapshot");
        jdbc.update("DELETE FROM map_place");
        SqlCapture.clear();
    }

    @Test
    void keepsMissingSnapshotTotalsNullForSourceFallback() {
        var result = snapshots.sumGlobalCounts();

        assertThat(result == null ? null : result.getClickCount()).isNull();
        assertThat(result == null ? null : result.getExposureCount()).isNull();
    }

    @Test
    void keepsRealZeroDistinctFromAnEmptySnapshotTable() {
        insertSnapshot(0L, 0L);

        var result = snapshots.sumGlobalCounts();

        assertThat(result).isNotNull();
        assertThat(result.getClickCount()).isZero();
        assertThat(result.getExposureCount()).isZero();
    }

    @Test
    void mapsBothGlobalSumsFromAllSnapshotRowsAsLongs() {
        insertSnapshot(3_000_000_000L, 4_000_000_000L);
        insertSnapshot(9L, 20L);

        var result = snapshots.sumGlobalCounts();

        assertThat(result).isNotNull();
        assertThat(result.getClickCount()).isEqualTo(3_000_000_009L);
        assertThat(result.getExposureCount()).isEqualTo(4_000_000_020L);
    }

    @Test
    void reflectsCommittedSnapshotChangesWithoutAnAdditionalCacheDelay() {
        insertSnapshot(10L, 100L);
        assertThat(read(true)).isEqualTo(read(false)).isEqualTo(new Totals(10L, 100L));

        jdbc.update("UPDATE " + TABLE + " SET click_count = click_count + 1, exposure_count = exposure_count + 10");

        assertThat(read(true)).isEqualTo(read(false)).isEqualTo(new Totals(11L, 110L));
    }

    @Test
    void comparesSeparateAndCombinedSumsOnTheSameDataAndConcurrency() throws Exception {
        var allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertThat(allocations.isThreadAllocatedMemorySupported()).isTrue();
        allocations.setThreadAllocatedMemoryEnabled(true);
        var mapper = new ObjectMapper();
        List<Comparison> comparisons = new ArrayList<>();
        Map<Integer, Object> plans = new LinkedHashMap<>();
        int samplesPerWorker = BENCHMARK ? 12 : 1;
        int[] volumes = BENCHMARK ? new int[]{10_000, 100_000, 1_000_000} : new int[]{1_000};
        int[] concurrencies = BENCHMARK ? new int[]{1, 8} : new int[]{1};

        for (int rows : volumes) {
            populateSnapshots(rows);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class)).isEqualTo(rows);
            Totals expected = read(false);
            assertThat(read(true)).isEqualTo(expected);
            plans.put(rows, Map.of(
                    "separateClick", mapper.readTree(explain("SELECT SUM(click_count) FROM " + TABLE)),
                    "separateExposure", mapper.readTree(explain("SELECT SUM(exposure_count) FROM " + TABLE)),
                    "combined", mapper.readTree(explain("SELECT SUM(click_count), SUM(exposure_count) FROM " + TABLE))));
            for (int concurrency : concurrencies) {
                comparisons.add(compare(rows, concurrency, samplesPerWorker, allocations, expected));
            }
        }

        Path output = Path.of("build/reports/global-totals-1843.json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                "baseline", "fc8834592765a149c425361dd75279a5829028e5",
                "current", "92afc490c04849583874ca097ad03fd43b250fe7",
                "java", System.getProperty("java.runtime.version"),
                "postgres", postgres.getDockerImageName(),
                "databaseVersion", jdbc.queryForObject("SELECT version()", String.class),
                "heapMaxBytes", Runtime.getRuntime().maxMemory(),
                "benchmarkEnabled", BENCHMARK,
                "warmupPerStrategyAndWorker", BENCHMARK ? 3 : 0,
                "comparisons", comparisons,
                "explainAnalyzeBuffers", plans));
    }

    private Comparison compare(int rows, int concurrency, int samplesPerWorker,
                               ThreadMXBean allocations, Totals expected) throws Exception {
        var ready = new CountDownLatch(concurrency);
        var start = new CountDownLatch(1);
        List<Measurement> separate = new ArrayList<>();
        List<Measurement> combined = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(concurrency)) {
            List<Future<List<Measurement>>> requests = new ArrayList<>();
            for (int worker = 0; worker < concurrency; worker++) {
                int workerId = worker;
                requests.add(executor.submit(() -> {
                    try {
                        if (BENCHMARK) {
                            for (int warmup = 0; warmup < 3; warmup++) {
                                read(false);
                                read(true);
                            }
                        }
                        ready.countDown();
                        assertThat(start.await(30, TimeUnit.SECONDS)).isTrue();
                        List<Measurement> measurements = new ArrayList<>();
                        for (int sample = 0; sample < samplesPerWorker; sample++) {
                            boolean combinedFirst = (workerId + sample) % 2 == 0;
                            measurements.add(measure(combinedFirst, allocations, expected));
                            measurements.add(measure(!combinedFirst, allocations, expected));
                        }
                        return measurements;
                    } finally {
                        ready.countDown();
                        SqlCapture.clear();
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var request : requests) {
                for (Measurement measurement : request.get(90, TimeUnit.SECONDS)) {
                    (measurement.combined() ? combined : separate).add(measurement);
                }
            }
        } finally {
            start.countDown();
        }
        return new Comparison(rows, concurrency, summarize(separate), summarize(combined));
    }

    private Measurement measure(boolean combined, ThreadMXBean allocations, Totals expected) {
        SqlCapture.reset();
        long allocatedBefore = allocations.getCurrentThreadAllocatedBytes();
        long started = System.nanoTime();
        Totals totals = read(combined);
        long elapsed = System.nanoTime() - started;
        long allocated = allocations.getCurrentThreadAllocatedBytes() - allocatedBefore;
        List<String> statements = SqlCapture.statements();
        assertThat(totals).isEqualTo(expected);
        assertThat(statements).hasSize(combined ? 1 : 2)
                .allMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("sum("));
        return new Measurement(combined, elapsed, allocated, statements.size());
    }

    private Totals read(boolean combined) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setTimeout(30);
        return transaction.execute(status -> {
            if (combined) {
                var result = snapshots.sumGlobalCounts();
                return new Totals(result == null ? null : result.getClickCount(),
                        result == null ? null : result.getExposureCount());
            }
            return new Totals(snapshots.sumClickCount(), snapshots.sumExposureCount());
        });
    }

    private void populateSnapshots(int rows) {
        // SUM 대상 테이블만 채운다. 이 스키마는 snapshot.place_id에 FK를 두지 않는다.
        jdbc.execute("TRUNCATE " + TABLE);
        jdbc.update("""
                INSERT INTO place_recommendation_snapshot
                  (place_id, photo_count, bookmark_count, total_like_count, click_count,
                   bookmark_conversion_count, like_conversion_count, exposure_count, updated_at)
                SELECT value, value % 11, value % 31, value % 71, value % 17,
                       value % 7, value % 13, value % 101 + 50, TIMESTAMP '2026-11-12 00:00:00'
                FROM generate_series(1, ?) value
                """, rows);
        jdbc.execute("VACUUM (ANALYZE) " + TABLE);
    }

    private String explain(String sql) {
        return jdbc.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, String.class);
    }

    private Summary summarize(List<Measurement> measurements) {
        List<Long> times = measurements.stream().map(Measurement::elapsedNanos).sorted().toList();
        return new Summary(times.size(), times.get(times.size() / 2) / 1_000_000d,
                times.get((int) Math.ceil(times.size() * .95) - 1) / 1_000_000d,
                measurements.stream().mapToLong(Measurement::allocatedBytes).average().orElseThrow(),
                measurements.stream().mapToInt(Measurement::sqlCount).min().orElseThrow(),
                measurements.stream().mapToInt(Measurement::sqlCount).max().orElseThrow());
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            if (sql.stripLeading().regionMatches(true, 0, "select", 0, 6)) {
                STATEMENTS.get().add(sql);
            }
            return sql;
        }

        static void reset() { STATEMENTS.get().clear(); }
        static List<String> statements() { return List.copyOf(STATEMENTS.get()); }
        static void clear() { STATEMENTS.remove(); }
    }

    private record Totals(Long clicks, Long exposures) {}
    private record Measurement(boolean combined, long elapsedNanos, long allocatedBytes, int sqlCount) {}
    private record Summary(int samples, double p50Millis, double p95Millis,
                           double meanAllocatedBytes, int minSqlCount, int maxSqlCount) {}
    private record Comparison(int rows, int concurrency, Summary separate, Summary combined) {}

    private void insertSnapshot(long clickCount, long exposureCount) {
        Long placeId = jdbc.queryForObject("""
                INSERT INTO map_place (place_name, address, latitude, longitude, location, registrant, photo_count)
                VALUES ('global-totals-test', 'address', 35.18, 128.10,
                        ST_SetSRID(ST_MakePoint(128.10, 35.18), 4326), 'integration-test', 0)
                RETURNING map_place_id
                """, Long.class);
        snapshots.saveAndFlush(PlaceRecommendationSnapshot.builder()
                .placeId(placeId)
                .clickCount(clickCount)
                .exposureCount(exposureCount)
                .updatedAt(LocalDateTime.now())
                .build());
    }
}
