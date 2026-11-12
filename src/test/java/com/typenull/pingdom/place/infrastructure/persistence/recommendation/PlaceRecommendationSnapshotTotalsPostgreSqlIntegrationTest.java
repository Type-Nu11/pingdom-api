package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import java.time.LocalDateTime;
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
        "spring.docker.compose.enabled=false", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false", "management.health.redis.enabled=false",
        "fcm.enabled=false", "outbox.enabled=false"
})
class PlaceRecommendationSnapshotTotalsPostgreSqlIntegrationTest {

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

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM place_recommendation_snapshot");
        jdbc.update("DELETE FROM map_place");
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
