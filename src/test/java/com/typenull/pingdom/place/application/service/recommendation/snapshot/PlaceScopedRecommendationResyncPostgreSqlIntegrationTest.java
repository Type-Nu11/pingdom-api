package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class PlaceScopedRecommendationResyncPostgreSqlIntegrationTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceRecommendationSnapshotResyncService resyncService;
    @Autowired private PlaceRecommendationSnapshotRepository recommendationSnapshotRepository;
    @Autowired private PlaceSimilaritySnapshotRepository similaritySnapshotRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM place_similarity_snapshot");
        jdbcTemplate.update("DELETE FROM place_recommendation_version_snapshot");
        jdbcTemplate.update("DELETE FROM place_recommendation_snapshot");
        jdbcTemplate.update("DELETE FROM map_place");
    }

    @Test
    void 단건_재동기화는_이동_전후_20km_pair만_교체한다() {
        Long targetPlaceId = insertPlace("target", 35.1801d, 128.1078d);
        Long oldNeighborId = insertPlace("old-neighbor", 35.1802d, 128.1079d);
        Long newNeighborId = insertPlace("new-neighbor", 36.0001d, 128.0001d);
        insertSimilaritySnapshot(targetPlaceId, newNeighborId);

        PlaceRecommendationSnapshotResyncService.SnapshotResyncResult initialResult =
                resyncService.resyncPlace(targetPlaceId);

        assertThat(initialResult.placeCount()).isEqualTo(1L);
        assertThat(initialResult.synchronizedSimilaritySnapshotCount()).isEqualTo(1L);
        assertThat(initialResult.deletedSimilaritySnapshotCount()).isEqualTo(1L);
        assertThat(recommendationSnapshotRepository.existsById(targetPlaceId)).isTrue();
        assertThat(recommendationSnapshotRepository.existsById(oldNeighborId)).isFalse();
        assertThat(otherPlaceIds(targetPlaceId)).containsExactly(oldNeighborId);

        updateCoordinates(targetPlaceId, 36.0002d, 128.0002d);

        PlaceRecommendationSnapshotResyncService.SnapshotResyncResult movedResult =
                resyncService.resyncPlace(targetPlaceId);

        assertThat(movedResult.synchronizedSimilaritySnapshotCount()).isEqualTo(1L);
        assertThat(movedResult.deletedSimilaritySnapshotCount()).isEqualTo(1L);
        assertThat(otherPlaceIds(targetPlaceId)).containsExactly(newNeighborId);
    }

    private Long insertPlace(String name, double latitude, double longitude) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name,
                    address,
                    latitude,
                    longitude,
                    location,
                    registrant,
                    photo_count
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326),
                    'integration-test',
                    0
                )
                RETURNING map_place_id
                """, Long.class, name, name + "-address", latitude, longitude, longitude, latitude);
    }

    private void updateCoordinates(Long placeId, double latitude, double longitude) {
        jdbcTemplate.update("""
                UPDATE map_place
                SET latitude = ?,
                    longitude = ?,
                    location = ST_SetSRID(ST_MakePoint(?, ?), 4326)
                WHERE map_place_id = ?
                """, latitude, longitude, longitude, latitude, placeId);
    }

    private void insertSimilaritySnapshot(Long firstPlaceId, Long secondPlaceId) {
        long leftPlaceId = Math.min(firstPlaceId, secondPlaceId);
        long rightPlaceId = Math.max(firstPlaceId, secondPlaceId);
        jdbcTemplate.update("""
                INSERT INTO place_similarity_snapshot (
                    left_place_id,
                    right_place_id,
                    geo_kernel_score,
                    co_bookmark_pmi_score,
                    co_like_cosine_score,
                    trend_similarity_score,
                    total_similarity_score,
                    updated_at
                ) VALUES (?, ?, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)
                """, leftPlaceId, rightPlaceId);
    }

    private List<Long> otherPlaceIds(Long targetPlaceId) {
        return similaritySnapshotRepository.findByPlaceId(targetPlaceId).stream()
                .map(snapshot -> otherPlaceId(snapshot, targetPlaceId))
                .toList();
    }

    private Long otherPlaceId(PlaceSimilaritySnapshot snapshot, Long targetPlaceId) {
        return snapshot.getLeftPlaceId().equals(targetPlaceId)
                ? snapshot.getRightPlaceId()
                : snapshot.getLeftPlaceId();
    }
}
