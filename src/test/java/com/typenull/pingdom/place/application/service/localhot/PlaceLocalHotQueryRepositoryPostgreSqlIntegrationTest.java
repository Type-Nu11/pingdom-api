package com.typenull.pingdom.place.application.service.localhot;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceLocalHotQueryRepository;
import java.time.LocalDateTime;
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
    @Autowired private MapPlaceRepository mapPlaceRepository;
    @Autowired private PlaceAdministrativeRegionRepository regionRepository;
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

    @Test
    void 지역_신규_저장과_기존_갱신_그리고_장소_regionCode_저장이_PostgreSQL에_반영된다() {
        Long placeId = insertPlace("행정구역 저장 장소", null);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 17, 10, 0);
        regionRepository.saveAndFlush(PlaceAdministrativeRegion.from(
                new ResolvedPlaceAdministrativeRegion("11680", "서울특별시", "강남구", "서울특별시 강남구"),
                createdAt
        ));

        PlaceAdministrativeRegion savedRegion = regionRepository.findById("11680").orElseThrow();
        LocalDateTime refreshedAt = createdAt.plusMinutes(1);
        savedRegion.refresh(
                new ResolvedPlaceAdministrativeRegion("11680", "서울특별시", "강남구", "강남구"),
                refreshedAt
        );
        regionRepository.saveAndFlush(savedRegion);
        jdbcTemplate.update("UPDATE map_place SET region_code = ? WHERE map_place_id = ?", "11680", placeId);

        assertThat(regionRepository.findById("11680").orElseThrow()).satisfies(region -> {
            assertThat(region.getSido()).isEqualTo("서울특별시");
            assertThat(region.getSigungu()).isEqualTo("강남구");
            assertThat(region.getRegionName()).isEqualTo("강남구");
            assertThat(region.getUpdatedAt()).isEqualTo(refreshedAt);
        });
        assertThat(mapPlaceRepository.findById(placeId).orElseThrow().getRegionCode()).isEqualTo("11680");
    }

    @Test
    void backfill_대상은_regionCode가_없는_기존_장소로만_제한된다() {
        Long missingRegionCodePlaceId = insertPlace("backfill 대상", null);
        Long assignedRegionCodePlaceId = insertPlace("backfill 제외", "11680");

        assertThat(mapPlaceRepository.findByRegionCodeIsNullOrderByIdAsc(PageRequest.of(0, 20)))
                .extracting(place -> place.getId())
                .containsExactly(missingRegionCodePlaceId)
                .doesNotContain(assignedRegionCodePlaceId);
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
