package com.typenull.pingdom.place.application.service.localhot;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceLocalHotQueryRepository;
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
class PlaceLocalHotQueryRepositoryPostgreSqlIntegrationTest {

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

    @Autowired private PlaceLocalHotQueryRepository queryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM map_bookmark");
        jdbcTemplate.update("DELETE FROM map_image");
        jdbcTemplate.update("DELETE FROM map_place");
        jdbcTemplate.update("DELETE FROM place_administrative_region");
    }

    @Test
    void 지역과_노출상태를_제한하고_현재_북마크수와_장소ID로_안정적으로_정렬한다() {
        insertRegion("11680", "서울특별시", "강남구");
        Long popularPlaceId = insertPlace("인기 장소", "11680");
        Long tiePlaceId = insertPlace("동률 장소", "11680");
        Long hiddenPlaceId = insertPlace("숨김 장소", "11680");
        Long otherRegionPlaceId = insertPlace("다른 지역", "47190");
        jdbcTemplate.update("UPDATE map_place SET discovery_status = 'HIDDEN' WHERE map_place_id = ?", hiddenPlaceId);
        insertBookmark(1L, popularPlaceId);
        insertBookmark(2L, popularPlaceId);
        insertBookmark(1L, tiePlaceId);
        insertBookmark(2L, tiePlaceId);
        insertBookmark(3L, hiddenPlaceId);
        insertBookmark(1L, otherRegionPlaceId);

        long total = queryRepository.countLocalHotPlaces("11680");
        List<PlaceLocalHotQueryRepository.PlaceLocalHotProjection> result = queryRepository.findLocalHotPlaces(
                "11680",
                1L,
                PageRequest.of(0, 20)
        );

        assertThat(total).isEqualTo(2L);
        assertThat(result).extracting(PlaceLocalHotQueryRepository.PlaceLocalHotProjection::getPlaceId)
                .containsExactly(tiePlaceId, popularPlaceId);
        assertThat(result).extracting(PlaceLocalHotQueryRepository.PlaceLocalHotProjection::getBookmarkCount)
                .containsOnly(2L);
        assertThat(result).extracting(PlaceLocalHotQueryRepository.PlaceLocalHotProjection::getBookmarked)
                .containsExactly(true, true);
    }

    private void insertRegion(String code, String sido, String sigungu) {
        jdbcTemplate.update(
                "INSERT INTO place_administrative_region (region_code, sido, sigungu, region_name, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                code,
                sido,
                sigungu,
                sido + " " + sigungu
        );
    }

    private Long insertPlace(String name, String regionCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name, address, category, latitude, longitude, location, registrant, photo_count, region_code
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 0, ?)
                RETURNING map_place_id
                """, Long.class, name, name + " 주소", "카페", 37.5d, 127.0d, 127.0d, 37.5d, "local-hot-test", regionCode);
    }

    private void insertBookmark(Long userId, Long placeId) {
        jdbcTemplate.update("INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", userId, placeId);
    }
}
