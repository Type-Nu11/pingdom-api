package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Checkpoint;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncCheckpointRepository.Phase;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.RecommendationResyncQueryRepository;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
class PlaceRecommendationFullResyncPostgreSqlIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("pingdom").withUsername("pingdom").withPassword("pingdom");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceRecommendationFullResyncService full;
    @Autowired private PlaceRecommendationResyncChunkService chunks;
    @Autowired private RecommendationResyncQueryRepository queries;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private RecommendationResyncCheckpointRepository checkpoints;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM map_image_like");
        jdbc.update("DELETE FROM map_image");
        jdbc.update("DELETE FROM map_bookmark");
        jdbc.update("DELETE FROM place_similarity_snapshot");
        jdbc.update("DELETE FROM place_recommendation_version_snapshot");
        jdbc.update("DELETE FROM place_recommendation_snapshot");
        jdbc.update("DELETE FROM map_place");
        jdbc.update("UPDATE recommendation_resync_checkpoint SET phase='DONE',owner_token=NULL,lease_until=NULL WHERE id=1");
    }

    @Test
    void completesSyncApiAndCleansActualOrphansAndInvalidPairs() {
        long first = place("first", 35.18);
        long second = place("second", 35.181);
        long far = place("far", 37.0);
        jdbc.update("""
                INSERT INTO place_recommendation_snapshot(place_id,photo_count,bookmark_count,total_like_count,
                click_count,bookmark_conversion_count,like_conversion_count,exposure_count,updated_at)
                VALUES (999999999,0,0,0,0,0,0,0,CURRENT_TIMESTAMP)
                """);
        pair(first, far);
        var result = full.resyncAll();
        assertThat(result.synchronizedSnapshotCount()).isEqualTo(3);
        assertThat(result.synchronizedSimilaritySnapshotCount()).isEqualTo(1);
        assertThat(result.deletedSnapshotCount()).isEqualTo(1);
        assertThat(result.deletedSimilaritySnapshotCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT phase FROM recommendation_resync_checkpoint WHERE id=1", String.class))
                .isEqualTo("DONE");
        assertThat(queries.signals(java.util.List.of(new RecommendationResyncQueryRepository.Pair(first, second))))
                .singleElement().satisfies(signal -> {
                    assertThat(signal.bookmarkIntersection()).isZero();
                    assertThat(signal.likeIntersection()).isZero();
                });
    }

    @Test
    void rollsBackFailedChunkAndResumesWithoutDoubleCountingAndFencesOldOwner() {
        place("single", 35.18);
        UUID first = UUID.randomUUID();
        chunks.claim(first);
        doThrow(new IllegalStateException("checkpoint write failed")).when(checkpoints).save(any(), any());
        assertThatThrownBy(() -> chunks.execute(first)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM place_recommendation_snapshot", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT cursor_left FROM recommendation_resync_checkpoint WHERE id=1", Long.class)).isZero();
        doCallRealMethod().when(checkpoints).save(any(), any());
        chunks.release(first);
        UUID second = UUID.randomUUID();
        chunks.claim(second);
        Checkpoint committed = chunks.execute(second);
        assertThat(committed.result().synchronizedSnapshotCount()).isEqualTo(1);
        assertThatThrownBy(() -> chunks.claim(UUID.randomUUID())).isInstanceOf(MapException.class);
        jdbc.update("UPDATE recommendation_resync_checkpoint SET lease_until=clock_timestamp()-interval '1 second' WHERE id=1");
        UUID third = UUID.randomUUID();
        chunks.claim(third);
        assertThatThrownBy(() -> chunks.execute(second)).isInstanceOf(MapException.class);
        chunks.release(second);
        Checkpoint completed;
        do {
            completed = chunks.execute(third);
        } while (completed.phase() != Phase.DONE);
        assertThat(completed.result().synchronizedSnapshotCount()).isEqualTo(1);
    }

    @Test
    void aggregatesDistinctReactionUsersWithoutLoadingTheirRows() {
        long left = place("left", 35.18);
        long right = place("right", 35.181);
        jdbc.update("""
                INSERT INTO map_bookmark(user_id,place_id,created_at) VALUES
                    (10,?,CURRENT_TIMESTAMP),(11,?,CURRENT_TIMESTAMP),
                    (11,?,CURRENT_TIMESTAMP),(12,?,CURRENT_TIMESTAMP)
                """, left, left, right, right);
        long firstImage = image(left, 2001);
        long secondImage = image(left, 2002);
        long rightImage = image(right, 2001);
        jdbc.update("""
                INSERT INTO map_image_like(user_id,map_image_id) VALUES (21,?),(21,?),(22,?),(22,?),(23,?)
                """, firstImage, secondImage, firstImage, rightImage, rightImage);
        assertThat(queries.signals(java.util.List.of(new RecommendationResyncQueryRepository.Pair(left, right))))
                .singleElement().satisfies(signal -> {
                    assertThat(signal.bookmarkLeft()).isEqualTo(2);
                    assertThat(signal.bookmarkRight()).isEqualTo(2);
                    assertThat(signal.bookmarkIntersection()).isEqualTo(1);
                    assertThat(signal.likeLeft()).isEqualTo(2);
                    assertThat(signal.likeRight()).isEqualTo(2);
                    assertThat(signal.likeIntersection()).isEqualTo(1);
                });
    }

    @Test
    void usesCanonicalPairCursorInDenseRegionAndRechecksCurrentCoordinatesBeforeDeletion() {
        long upper = 0;
        for (int i=0; i<34; i++) upper = place("dense-"+i, 35.18+i*0.00001);
        var first = queries.pairs(0, 0, upper);
        assertThat(first).hasSize(500);
        var last = first.getLast();
        var remaining = queries.pairs(last.left(), last.right(), upper);
        assertThat(remaining).hasSize(61);
        assertThat(remaining).doesNotContainAnyElementsOf(first);
        pair(first.getFirst().left(), first.getFirst().right());
        var existing = queries.existingPairs(0, Long.MAX_VALUE);
        long movedPlace = first.getFirst().right();
        jdbc.update("UPDATE map_place SET latitude=37,location=ST_SetSRID(ST_MakePoint(longitude,37),4326) WHERE map_place_id=?", movedPlace);
        // 조회 당시 이탈했던 쌍도 삭제 직전 현재 범위로 돌아오면 보존한다.
        assertThat(queries.signals(java.util.List.of(first.getFirst()))).isEmpty();
        jdbc.update("UPDATE map_place SET latitude=35.18,location=ST_SetSRID(ST_MakePoint(longitude,35.18),4326) WHERE map_place_id=?", movedPlace);
        assertThat(queries.deleteInvalidPairs(existing.stream().map(RecommendationResyncQueryRepository.ExistingPair::id).toList()))
                .isZero();
    }

    private long image(long place, long author) {
        return jdbc.queryForObject("""
                INSERT INTO map_image(image_url,s3_key,title,user_id,created_time,like_count,map_place_id)
                VALUES ('test','test','test',?,CURRENT_TIMESTAMP,0,?) RETURNING map_image_id
                """, Long.class, author, place);
    }

    private long place(String name, double latitude) {
        return jdbc.queryForObject("""
                INSERT INTO map_place(place_name,address,latitude,longitude,location,registrant,photo_count)
                VALUES (?,?,?,128.1,ST_SetSRID(ST_MakePoint(128.1,?),4326),'resync-test',0)
                RETURNING map_place_id
                """, Long.class, name, name, latitude, latitude);
    }

    private void pair(long left, long right) {
        jdbc.update("""
                INSERT INTO place_similarity_snapshot(left_place_id,right_place_id,geo_kernel_score,
                co_bookmark_pmi_score,co_like_cosine_score,trend_similarity_score,total_similarity_score,updated_at)
                VALUES (?,?,0,0,0,0,0,CURRENT_TIMESTAMP)
                """, Math.min(left,right), Math.max(left,right));
    }
}
