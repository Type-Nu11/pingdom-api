package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class PlaceRecommendationSnapshotConcurrencyPostgreSqlIntegrationTest {
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

    @Autowired private PlaceRecommendationClickService clicks;
    @Autowired private MapPlaceRepository places;
    @Autowired private PlaceRecommendationSnapshotService snapshotWriter;
    @Autowired private PlaceRecommendationSnapshotWriteLock snapshotWriteLock;
    @Autowired private PlaceRecommendationClickRepository clickRows;
    @Autowired private PlaceRecommendationSnapshotRepository snapshots;
    @Autowired private PlaceRecommendationVersionSnapshotRepository versionSnapshots;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private PlaceRecommendationVersionSnapshotService versionWriter;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM place_recommendation_click");
        jdbc.update("DELETE FROM place_recommendation_snapshot");
        jdbc.update("DELETE FROM place_recommendation_version_snapshot");
        jdbc.update("DELETE FROM map_place");
    }

    @Test
    void reloadsBothSnapshotsAfterAnotherTransactionCommitted() throws Exception {
        Long placeId = insertPlace();
        clicks.recordClick(null, placeId, "v1", "seed");
        var staleLoaded = new CountDownLatch(1);
        var firstCommitted = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayedWriter = executor.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(snapshots.findById(placeId).orElseThrow().getClickCount()).isEqualTo(1L);
                assertThat(versionSnapshots.findByPlaceIdAndRecommendationVersion(placeId, "v1")
                        .orElseThrow().getClickCount()).isEqualTo(1L);
                staleLoaded.countDown();
                await(firstCommitted);
                clicks.recordClick(null, placeId, "v1", "second");
            }));
            try {
                assertThat(staleLoaded.await(10, TimeUnit.SECONDS)).isTrue();
                clicks.recordClick(null, placeId, "v1", "first");
            } finally {
                firstCommitted.countDown();
            }
            delayedWriter.get(15, TimeUnit.SECONDS);
        }
        assertThat(clickRows.countByPlaceId(placeId)).isEqualTo(3L);
        assertThat(snapshots.findById(placeId).orElseThrow().getClickCount()).isEqualTo(3L);
        assertThat(versionSnapshots.findByPlaceIdAndRecommendationVersion(placeId, "v1")
                .orElseThrow().getClickCount()).isEqualTo(3L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reloadsPreloadedPlaceCountBeforeSnapshotCreationOrRefresh(boolean snapshotExists) throws Exception {
        Long placeId = insertPlace();
        if (snapshotExists) {
            clicks.recordClick(null, placeId, "v1", "seed");
        }
        var staleLoaded = new CountDownLatch(1);
        var countCommitted = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var delayedWriter = executor.submit(() -> transaction.executeWithoutResult(status -> {
                var preloadedPlace = places.findById(placeId).orElseThrow();
                assertThat(preloadedPlace.currentPhotoCount()).isZero();
                staleLoaded.countDown();
                await(countCommitted);
                snapshotWriter.refresh(placeId);
                assertThat(preloadedPlace.currentPhotoCount()).isEqualTo(5L);
            }));
            try {
                assertThat(staleLoaded.await(10, TimeUnit.SECONDS)).isTrue();
                jdbc.update("UPDATE map_place SET photo_count = 5 WHERE map_place_id = ?", placeId);
            } finally {
                countCommitted.countDown();
            }
            delayedWriter.get(15, TimeUnit.SECONDS);
        }
        var snapshot = snapshots.findById(placeId).orElseThrow();
        assertThat(snapshot.getPhotoCount()).isEqualTo(5L);
        assertThat(snapshot.getClickCount()).isEqualTo(snapshotExists ? 1L : 0L);
    }

    @Test
    void preservesLocalPlaceChangesWhenRefreshingAnAlreadyLockedPlace() {
        Long placeId = insertPlace();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var place = snapshotWriteLock.requirePlace(placeId);
            place.replacePhotoCount(7L);
            snapshotWriter.refresh(placeId);
            assertThat(place.currentPhotoCount()).isEqualTo(7L);
        });
        assertThat(places.findById(placeId).orElseThrow().currentPhotoCount()).isEqualTo(7L);
        assertThat(snapshots.findById(placeId).orElseThrow().getPhotoCount()).isEqualTo(7L);
    }

    @Test
    void versionFailureRollsBackSourceAndTotalIncrement() {
        Long placeId = insertPlace();
        clicks.recordClick(null, placeId, "v1", "seed");
        doThrow(new IllegalStateException("controlled version write failure")).when(versionWriter)
                .increaseClickCounts(List.of(placeId), "failed");

        assertThatThrownBy(() -> clicks.recordClick(null, placeId, "failed", "failed"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(clickRows.countByPlaceId(placeId)).isEqualTo(1L);
        assertThat(snapshots.findById(placeId).orElseThrow().getClickCount()).isEqualTo(1L);
        assertThat(versionSnapshots.findByPlaceIdAndRecommendationVersion(placeId, "failed")).isEmpty();
    }

    private Long insertPlace() {
        return jdbc.queryForObject("""
                INSERT INTO map_place (place_name, address, latitude, longitude, location, registrant, photo_count)
                VALUES ('snapshot-test', 'address', 35.18, 128.10,
                        ST_SetSRID(ST_MakePoint(128.10, 35.18), 4326), 'integration-test', 0)
                RETURNING map_place_id
                """, Long.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("transaction synchronization timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
