package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingPeriod;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingResponse;
import com.typenull.pingdom.place.api.dto.ranking.PlaceRankingScope;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
        "spring.flyway.locations=classpath:db/test-pre-migration,classpath:db/migration",
        "spring.flyway.postgresql.transactional-lock=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.cloud.aws.s3.enabled=false",
        "management.health.redis.enabled=false",
        "fcm.enabled=false",
        "outbox.enabled=false"
})
class PlaceRankingQueryServicePostgreSqlIntegrationTest {

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

    @Autowired private PlaceRankingQueryService placeRankingQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM map_bookmark");
        jdbcTemplate.update("DELETE FROM map_image");
        jdbcTemplate.update("DELETE FROM map_place");
    }

    @Test
    void local_랭킹은_반경을_한번만_확장하고_기간내_활성_게시물만_집계한다() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Long firstPlaceId = insertPlace("가까운 A", "카페", 35.1801d, 128.1078d);
        Long secondPlaceId = insertPlace("가까운 B", "카페", 35.1802d, 128.1079d);
        Long expandedPlaceId = insertPlace("확장 C", "카페", 35.2300d, 128.1078d);
        Long excludedPlaceId = insertPlace("숨김 D", "카페", 35.1803d, 128.1080d);

        Long firstRepresentativeId = insertImage(firstPlaceId, "first-representative", 10, now.minusHours(2), "ACTIVE");
        insertImage(firstPlaceId, "first-tied", 10, now.minusHours(1), "ACTIVE");
        insertImage(secondPlaceId, "second", 20, now.minusHours(2), "ACTIVE");
        insertImage(expandedPlaceId, "expanded", 15, now.minusHours(2), "ACTIVE");
        insertImage(excludedPlaceId, "hidden", 100, now.minusHours(1), "AUTO_HIDDEN");
        insertImage(excludedPlaceId, "stale", 100, now.minusDays(8), "ACTIVE");
        jdbcTemplate.update(
                "INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, ?)",
                77L,
                secondPlaceId,
                now
        );

        statistics().clear();

        PlaceRankingResponse result = placeRankingQueryService.find(
                PlaceRankingScope.LOCAL,
                35.1801d,
                128.1078d,
                1.0d,
                PlaceRankingPeriod.WEEK,
                "카페",
                1,
                3,
                77L
        );

        assertThat(result.radiusExpanded()).isTrue();
        assertThat(result.requestedRadiusKm()).isEqualTo(1.0d);
        assertThat(result.appliedRadiusKm()).isEqualTo(50.0d);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.items()).extracting(PlaceRankingResponse.Item::placeId)
                .containsExactly(firstPlaceId, secondPlaceId, expandedPlaceId);
        assertThat(result.items().getFirst())
                .extracting(
                        PlaceRankingResponse.Item::rank,
                        PlaceRankingResponse.Item::likeCount,
                        PlaceRankingResponse.Item::postCount,
                        PlaceRankingResponse.Item::representativePostId,
                        PlaceRankingResponse.Item::imageUrl
                )
                .containsExactly(1, 20L, 2L, firstRepresentativeId, "https://example.com/first-representative.jpg");
        assertThat(result.items().get(1).bookmarked()).isTrue();
        assertThat(statistics().getPrepareStatementCount())
                .as("반경 확장 시에도 count 두 번, 페이지 조회 한 번, bookmark batch 한 번만 실행한다")
                .isLessThanOrEqualTo(4L);
    }

    @Test
    void national_랭킹은_day_week_month_기간과_페이지_순위를_유지한다() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Long dayPlaceId = insertPlace("DAY", "관광", 35.1801d, 128.1078d);
        Long weekPlaceId = insertPlace("WEEK", "관광", 35.1802d, 128.1079d);
        Long monthPlaceId = insertPlace("MONTH", "관광", 35.1803d, 128.1080d);
        insertImage(dayPlaceId, "day", 30, now.minusHours(2), "ACTIVE");
        insertImage(weekPlaceId, "week", 20, now.minusDays(2), "ACTIVE");
        insertImage(monthPlaceId, "month", 10, now.minusDays(8), "ACTIVE");

        PlaceRankingResponse day = findNational(PlaceRankingPeriod.DAY, 1, 10);
        PlaceRankingResponse week = findNational(PlaceRankingPeriod.WEEK, 1, 10);
        PlaceRankingResponse monthPageTwo = findNational(PlaceRankingPeriod.MONTH, 2, 1);

        assertThat(day.items()).extracting(PlaceRankingResponse.Item::placeName).containsExactly("DAY");
        assertThat(week.items()).extracting(PlaceRankingResponse.Item::placeName).containsExactly("DAY", "WEEK");
        assertThat(monthPageTwo.items()).extracting(PlaceRankingResponse.Item::placeName).containsExactly("WEEK");
        assertThat(monthPageTwo.items().getFirst().rank()).isEqualTo(2);
        assertThat(monthPageTwo.totalCount()).isEqualTo(3);
        assertThat(monthPageTwo.hasNext()).isTrue();
    }

    private PlaceRankingResponse findNational(PlaceRankingPeriod period, int page, int limit) {
        return placeRankingQueryService.find(
                PlaceRankingScope.NATIONAL,
                null,
                null,
                null,
                period,
                null,
                page,
                limit,
                null
        );
    }

    private Long insertPlace(String name, String category, double latitude, double longitude) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name, address, category, latitude, longitude, location, registrant, photo_count
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 0)
                RETURNING map_place_id
                """, Long.class, name, name + " 주소", category, latitude, longitude, longitude, latitude, "ranking-test");
    }

    private Long insertImage(Long placeId, String name, long likeCount, LocalDateTime createdAt, String visibilityStatus) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_image (
                    image_url, s3_key, title, map_place_id, created_time, like_count, visibility_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING map_image_id
                """, Long.class,
                "https://example.com/" + name + ".jpg",
                "map/" + name + ".jpg",
                name,
                placeId,
                createdAt,
                likeCount,
                visibilityStatus
        );
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
