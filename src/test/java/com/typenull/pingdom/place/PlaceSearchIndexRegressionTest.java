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
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

    @Test
    void 장소_키워드_검색은_trgm_인덱스를_사용한다() throws Exception {
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

    @Test
    void 정규화_주소_검색은_trgm_인덱스를_사용한다() throws Exception {
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

    @Test
    void 장소_반경_검색은_latitude_longitude_btree_인덱스를_사용한다() throws Exception {
        List<String> planLines = explain("""
                SELECT mp.map_place_id
                FROM map_place mp
                WHERE mp.latitude BETWEEN 35.1200 AND 35.2400
                  AND mp.longitude BETWEEN 128.0400 AND 128.1600
                  AND (
                      6371000.0 * 2.0 * ASIN(SQRT(
                          POWER(SIN(RADIANS(mp.latitude - 35.1801) / 2.0), 2.0)
                          + COS(RADIANS(35.1801)) * COS(RADIANS(mp.latitude))
                          * POWER(SIN(RADIANS(mp.longitude - 128.1078) / 2.0), 2.0)
                      ))
                  ) <= 5000
                ORDER BY mp.map_place_id DESC
                LIMIT 20
                """);

        assertThat(planLines)
                .anyMatch(line -> line.contains("idx_map_place_latitude_longitude"));
    }

    @Test
    void 추천_후보_바운딩박스_조회는_latitude_longitude_btree_인덱스를_사용한다() throws Exception {
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
