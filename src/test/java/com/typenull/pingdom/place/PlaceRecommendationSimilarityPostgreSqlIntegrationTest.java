package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.ThreadMXBean;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService.PlacePairKey;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService.SimilarityContext;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService.SimilarityScore;
import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceRecommendationSimilarityService.TrendProfile;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.observability.RecommendationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 JPA projection의 전송·할당 비용을 비교하며 HTTP 전체 추천 성능으로 해석하지 않는다. */
@Tag("postgres-integration")
@Testcontainers
@DataJpaTest(showSql = false, properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.typenull.pingdom.place.PlaceRecommendationSimilarityPostgreSqlIntegrationTest$SqlCapture"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlaceRecommendationSimilarityPostgreSqlIntegrationTest {

    private static final int PLACE_COUNT = 8;
    private static final int WARMUP = 3;
    private static final int SAMPLES = 12;
    private static final boolean BENCHMARK = Boolean.parseBoolean(System.getenv("PINGDOM_SIMILARITY_BENCHMARK"));
    private static final List<Comparison> RESULTS = new ArrayList<>();
    private static final LocalDateTime STORED_AT = LocalDateTime.of(2010, 1, 1, 0, 0);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private MapBookmarkRepository bookmarks;
    @Autowired private MapImageLikeRepository likes;
    @Autowired private PlaceRecommendationSnapshotRepository recommendationSnapshots;
    @Autowired private PlaceSimilaritySnapshotRepository similaritySnapshots;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private Map<Long, MapPlace> places;
    private Map<Long, List<Long>> imageIds;
    private QueryStats stats;
    private PlaceRecommendationSimilarityService service;
    private MapBookmarkRepository measuredBookmarks;
    private MapImageLikeRepository measuredLikes;
    private PlaceRecommendationSnapshotRepository measuredRecommendationSnapshots;
    private PlaceSimilaritySnapshotRepository measuredSimilaritySnapshots;
    private long totalBookmarkUsers;

    @BeforeEach
    void prepareDatabase() {
        jdbc.update("DELETE FROM place_similarity_snapshot");
        jdbc.update("DELETE FROM place_recommendation_snapshot");
        jdbc.update("DELETE FROM map_image_like");
        jdbc.update("DELETE FROM map_bookmark");
        jdbc.update("DELETE FROM map_image");
        jdbc.update("DELETE FROM map_place");
        places = new LinkedHashMap<>();
        imageIds = new LinkedHashMap<>();
        transaction(false).executeWithoutResult(status -> {
            for (int index = 0; index < PLACE_COUNT; index++) {
                MapPlace place = MapPlace.builder().name("similarity-" + index).address("fixture")
                        .latitude(35.18 + index * 0.0002).longitude(128.10 + index * 0.0001)
                        .registrant("fixture").photoCount(20L + index).build();
                entityManager.persist(place);
                places.put(place.getId(), place);
                List<Long> images = new ArrayList<>();
                for (int copy = 0; copy < 2; copy++) {
                    MapImage image = MapImage.builder().imageUrl("https://invalid.test/fixture")
                            .s3Key("fixture/" + index + "/" + copy).title("fixture")
                            .mapPlace(place).build();
                    entityManager.persist(image);
                    images.add(image.getId());
                }
                imageIds.put(place.getId(), images);
            }
        });
        stats = new QueryStats();
        measuredBookmarks = observe(MapBookmarkRepository.class, bookmarks);
        measuredLikes = observe(MapImageLikeRepository.class, likes);
        measuredRecommendationSnapshots = observe(PlaceRecommendationSnapshotRepository.class, recommendationSnapshots);
        measuredSimilaritySnapshots = observe(PlaceSimilaritySnapshotRepository.class, similaritySnapshots);
        service = new PlaceRecommendationSimilarityService(measuredBookmarks, measuredLikes,
                measuredRecommendationSnapshots, measuredSimilaritySnapshots,
                new RecommendationMetrics(new SimpleMeterRegistry()));
    }

    static Stream<Arguments> coverageAndVolume() {
        return Stream.of(1_000, 10_000).flatMap(volume -> Stream.of("full", "partial", "miss")
                .map(coverage -> Arguments.of(volume, coverage)));
    }

    @ParameterizedTest(name = "{1}, {0} users/place")
    @MethodSource("coverageAndVolume")
    void preservesScoresAndBoundsActualReactionRows(int usersPerPlace, String coverage) throws Exception {
        reactions(usersPerPlace);
        storedScores(coverage);

        Observation before = request(true);
        Observation after = request(false);
        assertEquivalent(before, after);
        assertThat(before.bookmarkRows()).isEqualTo((long) PLACE_COUNT * usersPerPlace);
        assertThat(before.likeRows()).isEqualTo((long) PLACE_COUNT * usersPerPlace * 2);
        int fallbackPlaces = switch (coverage) {
            case "full" -> 0;
            case "partial" -> 2;
            default -> PLACE_COUNT;
        };
        assertThat(after.bookmarkRows()).isEqualTo((long) fallbackPlaces * usersPerPlace);
        assertThat(after.likeRows()).isEqualTo((long) fallbackPlaces * usersPerPlace * 2);
        assertThat(after.queries()).isEqualTo(fallbackPlaces == 0 ? 2 : 4);
        assertThat(before.queries()).isEqualTo(4);

        if (BENCHMARK) {
            for (int warmup = 0; warmup < WARMUP; warmup++) {
                request(true);
                request(false);
            }
            List<Measurement> eager = new ArrayList<>();
            List<Measurement> snapshotFirst = new ArrayList<>();
            for (int sample = 0; sample < SAMPLES; sample++) {
                if (sample % 2 == 0) {
                    eager.add(measure(true));
                    snapshotFirst.add(measure(false));
                } else {
                    snapshotFirst.add(measure(false));
                    eager.add(measure(true));
                }
            }
            RESULTS.add(new Comparison(usersPerPlace, coverage, summarize(eager), summarize(snapshotFirst)));
        }
    }

    @Test
    void rebuildStillReadsAllRawRowsAndDeduplicatesLikesAcrossImages() {
        reactions(1_000);
        storedScores("full");
        transaction(true).executeWithoutResult(status -> {
            stats.reset();
            SimilarityContext context = service.buildContext(places.keySet(), places, false, totalBookmarkUsers);
            assertThat(stats.bookmarkRows).isEqualTo(8_000);
            assertThat(stats.likeRows).isEqualTo(16_000);
            assertThat(context.snapshotScoresByPair()).isEmpty();
            assertThat(context.likeUsersByPlace().values()).allSatisfy(users -> assertThat(users).hasSize(1_000));
            Long first = places.keySet().iterator().next();
            Long second = places.keySet().stream().skip(1).findFirst().orElseThrow();
            assertThat(service.score(first, second, context).snapshotBacked()).isFalse();
        });
    }

    private void reactions(int usersPerPlace) {
        int index = 0;
        for (Map.Entry<Long, List<Long>> entry : imageIds.entrySet()) {
            long offset = (long) (index++ % 4) * usersPerPlace / 4;
            jdbc.update("""
                    INSERT INTO map_bookmark(user_id, place_id, created_at)
                    SELECT value + ?, ?, TIMESTAMP '2026-11-01 00:00:00'
                    FROM generate_series(1, ?) value
                    """, offset, entry.getKey(), usersPerPlace);
            for (Long imageId : entry.getValue()) {
                jdbc.update("""
                        INSERT INTO map_image_like(user_id, map_image_id)
                        SELECT value + ?, ? FROM generate_series(1, ?) value
                        """, offset, imageId, usersPerPlace);
            }
        }
        totalBookmarkUsers = bookmarks.countDistinctUserId();
        jdbc.execute("ANALYZE map_bookmark");
        jdbc.execute("ANALYZE map_image_like");
        jdbc.execute("ANALYZE map_image");
    }

    private void storedScores(String coverage) {
        if (coverage.equals("miss")) {
            return;
        }
        transaction(false).executeWithoutResult(status -> {
            SimilarityContext realtime = service.buildContext(places.keySet(), places, false, totalBookmarkUsers);
            List<Long> ids = new ArrayList<>(places.keySet());
            for (int left = 0; left < ids.size(); left++) {
                for (int right = left + 1; right < ids.size(); right++) {
                    if (coverage.equals("partial") && left == 0 && right == 1) {
                        continue;
                    }
                    SimilarityScore score = service.score(ids.get(left), ids.get(right), realtime);
                    entityManager.persist(PlaceSimilaritySnapshot.builder()
                            .leftPlaceId(ids.get(left)).rightPlaceId(ids.get(right))
                            .geoKernelScore(score.geoKernel()).coBookmarkPmiScore(score.coBookmarkPmi())
                            .coLikeCosineScore(score.coLikeCosine()).trendSimilarityScore(score.trendSimilarity())
                            .totalSimilarityScore(score.totalSimilarity()).updatedAt(STORED_AT).build());
                }
            }
        });
        jdbc.execute("ANALYZE place_similarity_snapshot");
    }

    private Observation request(boolean eager) {
        return transaction(true).execute(status -> {
            entityManager.clear();
            stats.reset();
            SqlCapture.reset();
            SimilarityContext context = eager ? eagerContext() :
                    service.buildContext(places.keySet(), places, true, totalBookmarkUsers);
            Map<PlacePairKey, SimilarityScore> scores = new LinkedHashMap<>();
            Map<Long, Double> affinity = new HashMap<>();
            List<Long> ids = new ArrayList<>(places.keySet());
            for (int left = 0; left < ids.size(); left++) {
                for (int right = left + 1; right < ids.size(); right++) {
                    SimilarityScore score = service.score(ids.get(left), ids.get(right), context);
                    scores.put(PlacePairKey.of(ids.get(left), ids.get(right)), score);
                    affinity.merge(ids.get(left), score.totalSimilarity(), Double::sum);
                    affinity.merge(ids.get(right), score.totalSimilarity(), Double::sum);
                }
            }
            List<Long> ranking = ids.stream().sorted(Comparator
                    .comparingDouble((Long id) -> affinity.get(id)).reversed().thenComparingLong(Long::longValue)).toList();
            return new Observation(scores, ranking, stats.bookmarkRows, stats.likeRows,
                    SqlCapture.selectCount(), stats.rawQueryNanos);
        });
    }

    /** fe5f8d3의 buildContext 순서·집합 의미만 재현하고 점수 함수·schema·runtime은 동일하게 둔다. */
    private SimilarityContext eagerContext() {
        Map<Long, Set<Long>> bookmarkUsers = new HashMap<>();
        for (var row : measuredBookmarks.findBookmarkUsersByPlaceIds(places.keySet())) {
            bookmarkUsers.computeIfAbsent(row.getPlaceId(), ignored -> new HashSet<>()).add(row.getUserId());
        }
        Map<Long, Set<Long>> likeUsers = new HashMap<>();
        for (var row : measuredLikes.findLikeUsersByPlaceIds(places.keySet())) {
            likeUsers.computeIfAbsent(row.getPlaceId(), ignored -> new HashSet<>()).add(row.getUserId());
        }
        Map<Long, TrendProfile> trends = new HashMap<>();
        for (var row : measuredRecommendationSnapshots.findByPlaceIdIn(places.keySet())) {
            trends.put(row.getPlaceId(), TrendProfile.of(row.getPhotoCount(), row.getLatestPostCreatedAt()));
        }
        Map<PlacePairKey, SimilarityScore> scores = new HashMap<>();
        for (var row : measuredSimilaritySnapshots.findByPlaceIdsWithin(places.keySet())) {
            scores.put(PlacePairKey.of(row.getLeftPlaceId(), row.getRightPlaceId()), new SimilarityScore(
                    row.getGeoKernelScore(), row.getCoBookmarkPmiScore(), row.getCoLikeCosineScore(),
                    row.getTrendSimilarityScore(), row.getTotalSimilarityScore(), true));
        }
        places.forEach((id, place) -> trends.putIfAbsent(id, TrendProfile.of(place.currentPhotoCount(), null)));
        return new SimilarityContext(places, bookmarkUsers, likeUsers, trends, scores, totalBookmarkUsers);
    }

    private void assertEquivalent(Observation eager, Observation current) {
        assertThat(current.ranking()).isEqualTo(eager.ranking());
        assertThat(current.scores()).isEqualTo(eager.scores());
    }

    private Measurement measure(boolean eager) throws Exception {
        // 앞 요청의 객체를 정리하여 시작 조건을 맞추며 GC 대기는 요청 시간에서 제외한다.
        System.gc();
        Thread.sleep(25);
        ThreadMXBean allocation = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertThat(allocation.isThreadAllocatedMemorySupported()).isTrue();
        allocation.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().threadId();
        try (HeapSampler heap = new HeapSampler()) {
            long allocatedBefore = allocation.getThreadAllocatedBytes(threadId);
            long started = System.nanoTime();
            Observation result = request(eager);
            long elapsed = System.nanoTime() - started;
            long allocatedBytes = allocation.getThreadAllocatedBytes(threadId) - allocatedBefore;
            heap.sample();
            return new Measurement(elapsed, allocatedBytes, heap.peakUsed,
                    heap.peakUsed - heap.initialUsed, result);
        }
    }

    private Summary summarize(List<Measurement> measurements) {
        List<Long> times = measurements.stream().map(Measurement::elapsedNanos).sorted().toList();
        return new Summary(measurements.size(), times.get(times.size() / 2) / 1_000_000d,
                times.get((int) Math.ceil(times.size() * .95) - 1) / 1_000_000d,
                measurements.stream().mapToLong(Measurement::allocatedBytes).average().orElseThrow(),
                measurements.stream().mapToLong(Measurement::peakUsedHeapBytes).max().orElseThrow(),
                measurements.stream().mapToLong(Measurement::peakHeapGrowthBytes).max().orElseThrow(),
                measurements.getFirst().result.bookmarkRows, measurements.getFirst().result.likeRows,
                measurements.getFirst().result.queries,
                measurements.stream().mapToLong(m -> m.result.rawQueryNanos).average().orElseThrow() / 1_000_000d);
    }

    @SuppressWarnings("unchecked")
    private <T> T observe(Class<T> type, T delegate) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            long started = System.nanoTime();
            Object result;
            try {
                result = method.invoke(delegate, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
            if (method.getName().equals("findBookmarkUsersByPlaceIds")) {
                stats.bookmarkRows += ((Collection<?>) result).size();
                stats.rawQueryNanos += System.nanoTime() - started;
            } else if (method.getName().equals("findLikeUsersByPlaceIds")) {
                stats.likeRows += ((Collection<?>) result).size();
                stats.rawQueryNanos += System.nanoTime() - started;
            }
            return result;
        });
    }

    private TransactionTemplate transaction(boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(readOnly);
        return template;
    }

    @AfterAll
    static void saveReport() throws IOException {
        if (!RESULTS.isEmpty()) {
            Path output = Path.of("build/reports/similarity-1844.json");
            Files.createDirectories(output.getParent());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                    "baseline", "fe5f8d378e8b7ce7b4dddbc52d5792f4ca838d1b",
                    "current", "92afc490c04849583874ca097ad03fd43b250fe7",
                    "java", System.getProperty("java.runtime.version"),
                    "postgres", POSTGRES.getDockerImageName(),
                    "heapMaxBytes", Runtime.getRuntime().maxMemory(),
                    "warmupPerStrategy", WARMUP,
                    "heapSamplingMillis", 1,
                    "measurements", RESULTS));
        }
    }

    private static class QueryStats {
        private long bookmarkRows;
        private long likeRows;
        private long rawQueryNanos;

        private void reset() {
            bookmarkRows = 0;
            likeRows = 0;
            rawQueryNanos = 0;
        }
    }

    /** 1ms 간격과 요청 직후 사용량으로 근사 peak를 측정하고 전체 할당량은 별도로 측정한다. */
    private static class HeapSampler implements AutoCloseable {
        private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        private final long initialUsed = memory.getHeapMemoryUsage().getUsed();
        private volatile long peakUsed = initialUsed;
        private volatile boolean running = true;
        private final Thread sampler;

        private HeapSampler() throws InterruptedException {
            CountDownLatch started = new CountDownLatch(1);
            sampler = Thread.ofPlatform().daemon().name("similarity-heap-sampler").start(() -> {
                started.countDown();
                while (running) {
                    sample();
                    try {
                        TimeUnit.MILLISECONDS.sleep(1);
                    } catch (InterruptedException stop) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
            started.await();
        }

        private synchronized void sample() {
            peakUsed = Math.max(peakUsed, memory.getHeapMemoryUsage().getUsed());
        }

        @Override
        public void close() throws InterruptedException {
            running = false;
            sampler.interrupt();
            sampler.join();
        }
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<Integer> SELECTS = ThreadLocal.withInitial(() -> 0);

        @Override
        public String inspect(String sql) {
            if (sql.stripLeading().regionMatches(true, 0, "select", 0, 6)) {
                SELECTS.set(SELECTS.get() + 1);
            }
            return sql;
        }

        static void reset() {
            SELECTS.set(0);
        }

        static int selectCount() {
            return SELECTS.get();
        }
    }

    private record Observation(Map<PlacePairKey, SimilarityScore> scores, List<Long> ranking,
                               long bookmarkRows, long likeRows, int queries, long rawQueryNanos) {}
    private record Measurement(long elapsedNanos, long allocatedBytes, long peakUsedHeapBytes,
                               long peakHeapGrowthBytes, Observation result) {}
    private record Summary(int samples, double p50Millis, double p95Millis, double meanAllocatedBytes,
                           long maxSampledHeapBytes, long maxSampledHeapGrowthBytes, long bookmarkRows,
                           long likeRows, int queries, double meanRawQueryMillis) {}
    private record Comparison(int usersPerPlace, String coverage, Summary eager, Summary snapshotFirst) {}
}
