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

    /**
     * 테스트 전용 PostGIS 컨테이너의 접속 정보를 Spring 데이터소스에 연결.
     */
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

    /**
     * 각 사례가 만든 북마크·이미지·장소·지역 행을 의존 순서대로 정리.
     */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM map_bookmark");
        jdbcTemplate.update("DELETE FROM map_image");
        jdbcTemplate.update("DELETE FROM map_place");
        jdbcTemplate.update("DELETE FROM place_administrative_region");
    }

    /**
     * 지정 지역의 공개 장소만 현재 북마크 수로 집계하고 동률 ID 순서와 사용자 북마크 여부를 PostgreSQL 결과로 확인.
     */
    @Test
    void ranksVisibleRegionalBookmarks() {
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

    /**
     * 행정구역 생성 후 이름·시각 갱신과 장소 지역 코드 변경이 실제 저장소 재조회에 반영되는지 확인.
     */
    @Test
    void persistsAdministrativeRegionUpdates() {
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

    /**
     * 지역 코드가 null인 장소만 백필 후보로 조회되는지 확인.
     */
    @Test
    void selectsMissingRegionBackfill() {
        Long missingRegionCodePlaceId = insertPlace("backfill 대상", null);
        Long assignedRegionCodePlaceId = insertPlace("backfill 제외", "11680");

        assertThat(mapPlaceRepository.findByRegionCodeIsNullOrderByIdAsc(PageRequest.of(0, 20)))
                .extracting(place -> place.getId())
                .containsExactly(missingRegionCodePlaceId)
                .doesNotContain(assignedRegionCodePlaceId);
    }

    /**
     * 지역 코드와 시도·시군구를 DB fixture로 삽입.
     */
    private void insertRegion(String code, String sido, String sigungu) {
        jdbcTemplate.update(
                "INSERT INTO place_administrative_region (region_code, sido, sigungu, region_name, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                code,
                sido,
                sigungu,
                sido + " " + sigungu
        );
    }

    /**
     * 지정 지역 코드와 공간 좌표를 가진 장소를 삽입하고 생성 ID를 반환.
     */
    private Long insertPlace(String name, String regionCode) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO map_place (
                    place_name, address, category, latitude, longitude, location, registrant, photo_count, region_code
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, 0, ?)
                RETURNING map_place_id
                """, Long.class, name, name + " 주소", "카페", 37.5d, 127.0d, 127.0d, 37.5d, "local-hot-test", regionCode);
    }

    /**
     * 사용자별 현재 북마크 수와 본인 저장 여부 검증용 행을 추가.
     */
    private void insertBookmark(Long userId, Long placeId) {
        jdbcTemplate.update("INSERT INTO map_bookmark (user_id, place_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)", userId, placeId);
    }
}
