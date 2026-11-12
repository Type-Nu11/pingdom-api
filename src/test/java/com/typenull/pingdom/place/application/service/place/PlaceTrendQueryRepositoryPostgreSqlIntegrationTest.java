package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceTrendQueryRepository;
import java.time.LocalDateTime;
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

    /**
     * 트렌드 원본 SQL을 실행할 테스트 PostGIS 데이터소스를 연결.
     */
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private PlaceTrendQueryRepository placeTrendQueryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 추세 이력과 현재 북마크·이미지·장소 행을 의존 순서대로 제거.
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM map_bookmark_trend_event");
        jdbcTemplate.update("DELETE FROM map_bookmark");
        jdbcTemplate.update("DELETE FROM map_image");
        jdbcTemplate.update("DELETE FROM map_place");
    }

    /**
     * 기간 내 반복 추가·해제를 사용자별 순증으로 계산하고 숨김 장소 제외·동률 정렬·현재 북마크 정보를 실제 SQL 결과로 확인.
     */
    @Test
    void deduplicatesBookmarkToggleGrowth() {
        LocalDateTime periodStart = LocalDateTime.of(2026, 8, 20, 12, 0);
        LocalDateTime periodEnd = periodStart.plusDays(7);
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

        List<PlaceTrendQueryRepository.PlaceTrendProjection> result = placeTrendQueryRepository.findTrendPage(
                periodStart,
                periodEnd,
                1L,
                0L,
                20
        );

        assertThat(result).allSatisfy(row -> assertThat(row.getTotalElements()).isEqualTo(2L));
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

    @Test
    void retainsTotalWhenThereAreNoTrendsOrThePageIsOutOfRange() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 20, 12, 0);
        LocalDateTime end = start.plusDays(7);

        var empty = placeTrendQueryRepository.findTrendPage(start, end, 1L, 0L, 20);

        assertThat(empty).singleElement().satisfies(row -> {
            assertThat(row.getPlaceId()).isNull();
            assertThat(row.getTotalElements()).isZero();
        });
        Long placeId = insertPlace("단일 트렌드", "카페");
        insertEvent(1L, placeId, placeId, "ADDED", start.plusHours(1));

        var outOfRange = placeTrendQueryRepository.findTrendPage(start, end, 1L, 20L, 20);

        assertThat(outOfRange).singleElement().satisfies(row -> {
            assertThat(row.getPlaceId()).isNull();
            assertThat(row.getTotalElements()).isEqualTo(1L);
        });
    }

    @Test
    void preservesPeriodBoundariesAndLastEventIdAtTheSameTime() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 20, 12, 0);
        LocalDateTime end = start.plusDays(7);
        Long placeId = insertPlace("기간 경계", "카페");
        insertEvent(1L, placeId, placeId, "ADDED", start);
        insertEvent(2L, placeId, placeId, "ADDED", end);
        insertEvent(3L, placeId, placeId, "ADDED", end.plusNanos(1000));
        insertEvent(4L, placeId, placeId, "BASELINE_ACTIVE", start.minusNanos(1000));
        insertEvent(4L, placeId, placeId, "REMOVED", start);
        insertEvent(5L, placeId, placeId, "ADDED", start.minusNanos(1000));
        insertEvent(5L, placeId, placeId, "REMOVED", end);
        insertEvent(6L, placeId, placeId, "ADDED", start);
        insertEvent(6L, placeId, placeId, "REMOVED", start);
        insertEvent(7L, placeId, placeId, "REMOVED", end);
        insertEvent(7L, placeId, placeId, "ADDED", end);

        var result = placeTrendQueryRepository.findTrendPage(start, end, 1L, 0L, 20);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.getPlaceId()).isEqualTo(placeId);
            assertThat(row.getTotalElements()).isEqualTo(1L);
            assertThat(row.getBookmarkAdds()).isEqualTo(3L);
            assertThat(row.getBookmarkRemoves()).isEqualTo(2L);
            assertThat(row.getNetBookmarkGrowth()).isEqualTo(1L);
        });
    }

    @Test
    void foldsMergedOriginStreamsBeforeCountingUserGrowth() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 20, 12, 0);
        Long placeId = insertPlace("병합 장소", "카페");
        // 기존 활성 origin은 같은 사용자의 새 origin 추가가 순증으로 세어지는 것을 막는다.
        insertEvent(10L, placeId, 1001L, "BASELINE_ACTIVE", start.minusSeconds(1));
        insertEvent(10L, placeId, 1002L, "ADDED", start.plusHours(1));
        insertEvent(11L, placeId, 2001L, "ADDED", start.plusHours(1));
        insertEvent(11L, placeId, 2002L, "ADDED", start.plusHours(1));
        insertEvent(12L, placeId, 3001L, "BASELINE_ACTIVE", start.minusSeconds(1));
        insertEvent(12L, placeId, 3002L, "BASELINE_ACTIVE", start.minusSeconds(1));
        insertEvent(12L, placeId, 3001L, "REMOVED", start.plusHours(1));

        var result = placeTrendQueryRepository.findTrendPage(start, start.plusDays(7), 1L, 0L, 20);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.getPlaceId()).isEqualTo(placeId);
            assertThat(row.getBookmarkAdds()).isEqualTo(1L);
            assertThat(row.getBookmarkRemoves()).isZero();
            assertThat(row.getNetBookmarkGrowth()).isEqualTo(1L);
        });
    }

    @Test
    void preservesAllTieBreakersAndJoinsRequesterAndImageDataForThePage() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 20, 12, 0);
        Long first = insertPlace("순증 우선", "카페");
        Long second = insertPlace("추가 수 우선", "카페");
        Long third = insertPlace("현재 북마크 수 하위", "카페");
        Long fourth = insertPlace("ID 하위", "카페");
        Long fifth = insertPlace("ID 상위", "카페");
        insertGrowth(first, 3, 0, start);
        insertGrowth(second, 4, 2, start);
        insertGrowth(third, 3, 1, start);
        insertGrowth(fourth, 3, 1, start);
        insertGrowth(fifth, 3, 1, start);
        insertBookmark(920L, third, start);
        insertBookmark(900L, fourth, start);
        insertBookmark(901L, fourth, start);
        insertBookmark(910L, fifth, start);
        insertBookmark(911L, fifth, start);
        insertImage(fifth, "https://example.com/old.jpg", "ACTIVE", start);
        insertImage(fifth, "https://example.com/latest.jpg", "ACTIVE", start);
        insertImage(fifth, "https://example.com/hidden.jpg", "HIDDEN", start.plusHours(1));
        insertImage(fourth, "https://example.com/fallback.jpg", "ACTIVE", start);
        jdbcTemplate.update("UPDATE map_place SET image_url = ? WHERE map_place_id = ?",
                "https://example.com/direct.jpg", fourth);

        var all = placeTrendQueryRepository.findTrendPage(start, start.plusDays(7), 900L, 0L, 20);
        var page = placeTrendQueryRepository.findTrendPage(start, start.plusDays(7), 900L, 2L, 2);

        assertThat(all).extracting(PlaceTrendQueryRepository.PlaceTrendProjection::getPlaceId)
                .containsExactly(first, second, fifth, fourth, third);
        assertThat(page).extracting(PlaceTrendQueryRepository.PlaceTrendProjection::getPlaceId)
                .containsExactly(fifth, fourth);
        assertThat(page).allSatisfy(row -> assertThat(row.getTotalElements()).isEqualTo(5L));
        assertThat(page).extracting(PlaceTrendQueryRepository.PlaceTrendProjection::getBookmarked)
                .containsExactly(false, true);
        assertThat(page).extracting(PlaceTrendQueryRepository.PlaceTrendProjection::getImageUrl)
                .containsExactly("https://example.com/latest.jpg", "https://example.com/direct.jpg");
    }

    private void insertGrowth(Long placeId, int adds, int removes, LocalDateTime start) {
        for (long userId = 1; userId <= adds; userId++) {
            insertEvent(userId, placeId, placeId, "ADDED", start.plusHours(1));
        }
        for (long userId = 100; userId < 100 + removes; userId++) {
            insertEvent(userId, placeId, placeId, "BASELINE_ACTIVE", start.minusSeconds(1));
            insertEvent(userId, placeId, placeId, "REMOVED", start.plusHours(1));
        }
    }

    private void insertBookmark(Long userId, Long placeId, LocalDateTime createdAt) {
        jdbcTemplate.update("INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, ?)",
                userId, placeId, createdAt);
    }

    private void insertImage(Long placeId, String imageUrl, String visibility, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO map_image (image_url, s3_key, title, like_count, map_place_id, visibility_status, created_time)
                VALUES (?, ?, '트렌드 이미지', 0, ?, ?, ?)
                """, imageUrl, imageUrl, placeId, visibility, createdAt);
    }

    /**
     * 동일 지역의 트렌드 비교용 장소를 생성.
     */
    private Long insertPlace(String name, String category) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name, address, category, latitude, longitude, location, registrant, photo_count
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 0)
                RETURNING map_place_id
                """, Long.class, name, name + " 주소", category, 35.18d, 128.10d, 128.10d, 35.18d, "trend-test");
    }

    /**
     * 사용자·현재 장소·원본 장소와 발생 시각을 지정한 북마크 상태 이력을 삽입.
     */
    private void insertEvent(Long userId, Long placeId, Long originPlaceId, String eventType, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO map_bookmark_trend_event (
                    user_id, place_id, origin_place_id, event_type, occurred_at
                ) VALUES (?, ?, ?, ?, ?)
                """, userId, placeId, originPlaceId, eventType, occurredAt);
    }
}
