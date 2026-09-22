package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("postgres-integration")
@Testcontainers
class PlaceSearchIndexRegressionTest {

    private static final DockerImageName POSTGIS_IMAGE = DockerImageName
            .parse("postgis/postgis:16-3.4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS_IMAGE)
            .withDatabaseName("pingdom")
            .withUsername("pingdom")
            .withPassword("pingdom");

    /**
     * PostGIS·trigram 확장과 전체 마이그레이션을 적용하고 검색 실행 계획 비교용 장소 데이터를 준비.
     */
    @BeforeAll
    static void setUp() throws Exception {
        resetDatabase();

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        }

        MigrateResult result = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                .baselineOnMigrate(false)
                .load()
                .migrate();

        assertThat(result.success).isTrue();

        seedPlaces();
    }

    /**
     * 순차 탐색을 비활성화한 EXPLAIN에서 이름·주소 부분 검색에 해당 trigram 인덱스 중 하나가 나타나는지 확인.
     */
    @Test
    void usesTrigramForKeywordSearch() throws Exception {
        List<String> planLines = explain("""
                SELECT mp.map_place_id
                FROM map_place mp
                WHERE LOWER(mp.place_name) LIKE '%남강로 626%'
                   OR LOWER(mp.address) LIKE '%남강로 626%'
                ORDER BY mp.map_place_id DESC
                LIMIT 20
                """);

        assertThat(planLines)
                .anyMatch(line -> line.contains("idx_map_place_name_trgm")
                        || line.contains("idx_map_place_address_trgm"));
    }

    /**
     * 순차 탐색을 비활성화한 도로명 주소 부분 검색 실행 계획에 정규화 주소 trigram 인덱스가 나타나는지 확인.
     */
    @Test
    void usesTrigramForRoadAddress() throws Exception {
        List<String> planLines = explain("""
                SELECT mp.map_place_id
                FROM map_place mp
                WHERE LOWER(mp.road_address) LIKE '%남강로 626%'
                ORDER BY mp.map_place_id DESC
                LIMIT 20
                """);

        assertThat(planLines)
                .anyMatch(line -> line.contains("idx_map_place_road_address_trgm"));
    }

    /**
     * 5km ST_DWithin 검색을 순차 탐색 없이 계획할 때 geography 변환용 GiST 인덱스가 나타나는지 확인.
     */
    @Test
    void usesGeographyIndexForRadius() throws Exception {
        List<String> planLines = explain("""
                SELECT mp.map_place_id
                FROM map_place mp
                WHERE mp.location IS NOT NULL
                  AND ST_DWithin(
                      mp.location::geography,
                      ST_SetSRID(ST_MakePoint(128.1078, 35.1801), 4326)::geography,
                      5000
                  )
                ORDER BY mp.map_place_id DESC
                LIMIT 20
                """);

        assertThat(planLines)
                .anyMatch(line -> line.contains("idx_map_place_location_geography_gist"));
    }

    /**
     * 공개·운영 중 장소의 지도 영역 검색을 순차 탐색 없이 계획할 때 geometry GiST 인덱스가 나타나는지 확인.
     */
    @Test
    void usesGeometryIndexForViewport() throws Exception {
        List<String> planLines = explain("""
                SELECT mp.map_place_id
                FROM map_place mp
                WHERE mp.location && ST_MakeEnvelope(128.04, 35.12, 128.16, 35.24, 4326)
                  AND mp.operating_status = 'OPERATING'
                  AND mp.discovery_status = 'VISIBLE'
                LIMIT 501
                """);

        assertThat(planLines)
                .anyMatch(line -> line.contains("idx_map_place_location_gist"));
    }

    /**
     * 위경도 구간과 근접 정렬을 사용하는 후보 조회를 순차 탐색 없이 계획할 때 좌표 B-tree 인덱스가 나타나는지 확인.
     */
    @Test
    void usesCoordinateIndexForCandidates() throws Exception {
        List<String> planLines = explain("""
                SELECT mp.map_place_id
                FROM map_place mp
                WHERE mp.latitude IS NOT NULL
                  AND mp.longitude IS NOT NULL
                  AND mp.latitude BETWEEN 35.1000 AND 35.2600
                  AND mp.longitude BETWEEN 128.0200 AND 128.1800
                ORDER BY ABS(mp.latitude - 35.1801)
                       + CASE
                             WHEN ABS(mp.longitude - 128.1078) <= 180.0 THEN ABS(mp.longitude - 128.1078)
                             ELSE 360.0 - ABS(mp.longitude - 128.1078)
                         END
                LIMIT 180
                """);

        assertThat(planLines)
                .anyMatch(line -> line.contains("idx_map_place_latitude_longitude"));
    }

    /**
     * 공간 기준 테이블을 제외한 public 테이블을 제거하여 빈 컨테이너 스키마에 마이그레이션을 적용.
     */
    private static void resetDatabase() throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$ DECLARE
                        r RECORD;
                    BEGIN
                        FOR r IN (
                            SELECT tablename
                            FROM pg_tables
                            WHERE schemaname = 'public'
                              AND tablename != 'spatial_ref_sys'
                        ) LOOP
                            EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.tablename) || ' CASCADE';
                        END LOOP;
                    END $$;
                    """);
        }
    }

    /**
     * 12,000개 일반 장소와 3개 표적 장소를 넣고 ANALYZE로 실행 계획에 필요한 통계를 갱신.
     */
    private static void seedPlaces() throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO map_place (
                        place_name,
                        address,
                        road_address,
                        category,
                        latitude,
                        longitude,
                        location,
                        registrant,
                        photo_count
                    )
                    SELECT
                        'seed-place-' || gs,
                        '경상남도 진주시 일반로 ' || gs,
                        '경상남도 진주시 일반로 ' || gs,
                        CASE WHEN gs % 3 = 0 THEN '카페' ELSE '식당' END,
                        35.0000 + ((gs % 1000) * 0.0003),
                        128.0000 + ((gs % 1000) * 0.0003),
                        ST_SetSRID(ST_MakePoint(
                            128.0000 + ((gs % 1000) * 0.0003),
                            35.0000 + ((gs % 1000) * 0.0003)
                        ), 4326),
                        'perf-tester',
                        0
                    FROM generate_series(1, 12000) AS gs
                    """);

            statement.executeUpdate("""
                    INSERT INTO map_place (
                        place_name,
                        address,
                        road_address,
                        category,
                        latitude,
                        longitude,
                        location,
                        registrant,
                        photo_count
                    ) VALUES
                    (
                        '진주성',
                        '경상남도 진주시 남강로 626',
                        '경상남도 진주시 남강로 626',
                        '관광',
                        35.1894,
                        128.0789,
                        ST_SetSRID(ST_MakePoint(128.0789, 35.1894), 4326),
                        'perf-tester',
                        0
                    ),
                    (
                        '남강 카페',
                        '경상남도 진주시 남강로 10',
                        '경상남도 진주시 남강로 10',
                        '카페',
                        35.1801,
                        128.1078,
                        ST_SetSRID(ST_MakePoint(128.1078, 35.1801), 4326),
                        'perf-tester',
                        0
                    ),
                    (
                        '가까운 장소',
                        '경상남도 진주시 가까운로 1',
                        '경상남도 진주시 가까운로 1',
                        '카페',
                        35.1802,
                        128.1079,
                        ST_SetSRID(ST_MakePoint(128.1079, 35.1802), 4326),
                        'perf-tester',
                        0
                    )
                    """);

            statement.execute("ANALYZE map_place");
        }
    }

    /**
     * 현재 연결에서 순차 탐색을 비활성화한 뒤 SQL을 실행하지 않고 EXPLAIN 계획 문자열을 수집.
     */
    private static List<String> explain(String sql) throws Exception {
        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");

            try (ResultSet resultSet = statement.executeQuery("EXPLAIN " + sql)) {
                List<String> planLines = new ArrayList<>();
                while (resultSet.next()) {
                    planLines.add(resultSet.getString(1));
                }
                return planLines;
            }
        }
    }
}
