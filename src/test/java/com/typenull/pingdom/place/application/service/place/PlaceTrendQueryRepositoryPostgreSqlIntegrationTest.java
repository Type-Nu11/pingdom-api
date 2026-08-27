package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceTrendQueryRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class PlaceTrendQueryRepositoryPostgreSqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceTrendQueryRepository placeTrendQueryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM map_bookmark_trend_event");
        jdbcTemplate.update("DELETE FROM map_bookmark");
        jdbcTemplate.update("DELETE FROM map_image");
        jdbcTemplate.update("DELETE FROM map_place");
    }

    @Test
    void 사용자별_시작과_종료_상태로_반복_토글을_한번만_집계하고_안정적으로_정렬한다() {
        LocalDateTime periodStart = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
        LocalDateTime periodEnd = LocalDateTime.now(ZoneOffset.UTC);
        Long firstPlaceId = insertPlace("토글 포함 트렌드", "카페");
        Long secondPlaceId = insertPlace("동률 트렌드", "카페");
        Long hiddenPlaceId = insertPlace("숨김 트렌드", "카페");
        jdbcTemplate.update("UPDATE map_place SET discovery_status = 'HIDDEN' WHERE map_place_id = ?", hiddenPlaceId);

        insertEvent(1L, firstPlaceId, firstPlaceId, "ADDED", periodStart.plusHours(1));
        insertEvent(1L, firstPlaceId, firstPlaceId, "REMOVED", periodStart.plusHours(2));
        insertEvent(1L, firstPlaceId, firstPlaceId, "ADDED", periodStart.plusHours(3));
        insertEvent(2L, firstPlaceId, firstPlaceId, "BASELINE_ACTIVE", periodStart.minusSeconds(1));
        insertEvent(2L, firstPlaceId, firstPlaceId, "REMOVED", periodStart.plusHours(1));
        insertEvent(3L, firstPlaceId, firstPlaceId, "ADDED", periodStart.plusHours(1));
        insertEvent(4L, secondPlaceId, secondPlaceId, "ADDED", periodStart.plusHours(1));
        insertEvent(5L, hiddenPlaceId, hiddenPlaceId, "ADDED", periodStart.plusHours(1));
        jdbcTemplate.update("INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, ?)", 1L, firstPlaceId, periodEnd);
        jdbcTemplate.update("INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, ?)", 3L, firstPlaceId, periodEnd);
        jdbcTemplate.update("INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, ?)", 4L, secondPlaceId, periodEnd);

        long total = placeTrendQueryRepository.countTrends(periodStart, periodEnd);
        List<PlaceTrendQueryRepository.PlaceTrendProjection> result = placeTrendQueryRepository.findTrends(
                periodStart,
                periodEnd,
                1L,
                PageRequest.of(0, 20)
        );

        assertThat(total).isEqualTo(2L);
        assertThat(result).extracting(PlaceTrendQueryRepository.PlaceTrendProjection::getPlaceId)
                .containsExactly(firstPlaceId, secondPlaceId);
        assertThat(result.getFirst())
                .extracting(
                        PlaceTrendQueryRepository.PlaceTrendProjection::getBookmarkAdds,
                        PlaceTrendQueryRepository.PlaceTrendProjection::getBookmarkRemoves,
                        PlaceTrendQueryRepository.PlaceTrendProjection::getNetBookmarkGrowth,
                        PlaceTrendQueryRepository.PlaceTrendProjection::getBookmarkCount,
                        PlaceTrendQueryRepository.PlaceTrendProjection::getBookmarked
                )
                .containsExactly(2L, 1L, 1L, 2L, true);
    }

    private Long insertPlace(String name, String category) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name, address, category, latitude, longitude, location, registrant, photo_count
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 0)
                RETURNING map_place_id
                """, Long.class, name, name + " 주소", category, 35.18d, 128.10d, 128.10d, 35.18d, "trend-test");
    }

    private void insertEvent(Long userId, Long placeId, Long originPlaceId, String eventType, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO map_bookmark_trend_event (
                    user_id, place_id, origin_place_id, event_type, occurred_at
                ) VALUES (?, ?, ?, ?, ?)
                """, userId, placeId, originPlaceId, eventType, occurredAt);
    }
}
