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

    /**
     * Flyway와 실제 공간 쿼리에 사용할 PostGIS 데이터소스를 테스트 컨테이너에 연결.
     */
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

    /**
     * 랭킹 사례의 북마크·이미지·장소 데이터를 정리.
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM map_bookmark");
        jdbcTemplate.update("DELETE FROM map_image");
        jdbcTemplate.update("DELETE FROM map_place");
    }

    /**
     * 1km 후보 부족 시 50km로 확장하여 기간 내 ACTIVE 게시물만 집계하고 동률·대표 이미지·북마크 및 SQL 4회 상한을 확인.
     */
    @Test
    void expandsLocalRankingOnce() {
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

    /**
     * 일·주·월에 포함되는 게시물을 구분하고 월별 두 번째 페이지의 절대 순위와 전체 건수를 유지하는지 확인.
     */
    @Test
    void preservesNationalRankingPeriods() {
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

    /**
     * 거리 조건 없는 전국 랭킹을 지정 기간과 페이지로 조회.
     */
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

    /**
     * 랭킹 거리와 카테고리 필터에 사용할 공간 좌표 장소를 DB에 삽입.
     */
    private Long insertPlace(String name, String category, double latitude, double longitude) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name, address, category, latitude, longitude, location, registrant, photo_count
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 0)
                RETURNING map_place_id
                """, Long.class, name, name + " 주소", category, latitude, longitude, longitude, latitude, "ranking-test");
    }

    /**
     * 좋아요 수·생성 시각·노출 상태를 지정한 게시물을 DB에 삽입.
     */
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

    /**
     * 랭킹 조회 횟수 상한을 검증할 Hibernate 통계를 가져옴.
     */
    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
