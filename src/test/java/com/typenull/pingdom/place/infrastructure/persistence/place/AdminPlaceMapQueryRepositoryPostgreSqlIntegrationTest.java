package com.typenull.pingdom.place.infrastructure.persistence.place;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapClusterToken;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapCondition;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapQueryService;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.shared.security.jwt.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 PostGIS에서 SQL 집계·변경 검증을 실행한다. 애플리케이션 전체 부트스트랩과 분리된 저장소 계약 테스트. */
@Tag("postgres-integration")
@Testcontainers
class AdminPlaceMapQueryRepositoryPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
    private JdbcTemplate jdbc;
    private AdminPlaceMapQueryRepository repository;
    private AdminPlaceMapQueryService service;
    private TransactionTemplate tx;
    private RecordingJdbc recording;

    @BeforeEach
    void setup() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        jdbc.execute("DROP TABLE IF EXISTS map_place, place_administrative_region");
        jdbc.execute("""
                CREATE TABLE map_place (map_place_id bigint PRIMARY KEY, place_name text, english_name text,
                    address text, category text, user_id bigint, latitude double precision, longitude double precision,
                    location geometry(Point,4326), discovery_status text, operating_status text, region_code varchar(5))
                """);
        jdbc.execute("CREATE INDEX idx_test_map_location ON map_place USING gist(location)");
        jdbc.execute("CREATE TABLE place_administrative_region (region_code varchar(5) PRIMARY KEY, sido text, region_name text)");
        jdbc.update("INSERT INTO place_administrative_region VALUES ('27260', '대구광역시', '대구광역시 수성구')");
        recording = new RecordingJdbc(ds);
        repository = new AdminPlaceMapQueryRepository(recording);
        service = new AdminPlaceMapQueryService(repository, new AdminPlaceMapClusterToken(new ObjectMapper(),
                new JwtProperties("test-map-".repeat(8), 60, 600)),
                new com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapProperties(200));
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setReadOnly(true);
    }

    private void place(long id, double lon, double lat, String region, String visibility) {
        jdbc.update("""
                INSERT INTO map_place VALUES (?, '카페', 'cafe', '대구 수성구', 'coffee', 7, ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?),4326), ?, 'OPERATING', ?)
                """, id, lat, lon, lon, lat, visibility, region);
    }

    private AdminPlaceMapRequest request(int zoom, PlaceDiscoveryStatus status) {
        return new AdminPlaceMapRequest(128.5,35.8,128.7,35.95,zoom,1200,800,null,null,status,null);
    }

    @Test
    void regionCountsWholeRegionIncludingUnlocatedWhileUnknownRegionsRemainVisible() {
        place(1,128.6,35.87,"27260","VISIBLE");
        place(2,128.9,35.87,"27260","HIDDEN"); // 화면 밖이지만 지역 전체 수에 포함
        place(3,128.61,35.88,null,"HIDDEN");
        place(4,128.62,35.89,"99999","VISIBLE");
        jdbc.update("INSERT INTO map_place(map_place_id,place_name,region_code,discovery_status,operating_status) VALUES (5,'좌표없음','27260','HIDDEN','OPERATING')");
        var response = tx.execute(s -> service.find(request(10,null)));
        assertThat(response.viewportPlaceCount()).isEqualTo(3);
        assertThat(response.regions()).singleElement().satisfies(r -> {
            assertThat(r.count()).isEqualTo(3);
            assertThat(r.bounds().east()).isEqualTo(128.9);
            assertThat(r.boundsSource()).isEqualTo("PLACE_EXTENT");
            assertThat(r.longitude()).isEqualTo(128.6);
            assertThat(r.latitude()).isEqualTo(35.87);
            assertThat(r.nextZoom()).isEqualTo(12);
        });
        long spatial = response.clusters().stream().mapToLong(c -> c.count()).sum() + response.markers().size();
        assertThat(spatial).isEqualTo(2);
        var sido = tx.execute(s -> service.find(request(7, null)));
        assertThat(sido.regions()).singleElement().satisfies(r -> assertThat(r.regionCode()).isEqualTo("27"));
    }

    @Test
    void filtersIncludeHiddenAndClosedByDefaultAndMatchCategoryAliasesAndRegistrantId() {
        place(1,128.6,35.87,null,"HIDDEN");
        place(2,128.61,35.88,null,"VISIBLE");
        jdbc.update("UPDATE map_place SET operating_status='PERMANENTLY_CLOSED' WHERE map_place_id=1");
        var hidden = new AdminPlaceMapRequest(128.5,35.8,128.7,35.95,14,1200,800,"7","커피",PlaceDiscoveryStatus.HIDDEN,null);
        var response = tx.execute(s -> service.find(hidden));
        assertThat(response.viewportPlaceCount()).isEqualTo(1);
        assertThat(response.markers()).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo(1);
            assertThat(m.category()).isEqualTo("CAFE");
            assertThat(m.operatingStatus()).isEqualTo("PERMANENTLY_CLOSED");
        });
    }

    @Test
    void colocatedPlacesRemainGroupedAndPagesRejectChangedMembership() {
        for (int i=1;i<=30;i++) place(i,128.6,35.87,null,"HIDDEN");
        var response = tx.execute(s -> service.find(request(20,null)));
        var group = response.clusters().getFirst();
        assertThat(group.count()).isEqualTo(30);
        assertThat(group.action()).isEqualTo("LIST");
        var page = tx.execute(s -> service.groupPlaces(group.clusterId(),2,20));
        assertThat(page.places()).hasSize(10);
        assertThat(page.places().getFirst().id()).isEqualTo(21);
        assertThat(page.totalCount()).isEqualTo(30);
        jdbc.update("UPDATE map_place SET longitude=128.61, location=ST_SetSRID(ST_MakePoint(128.61,latitude),4326) WHERE map_place_id=30");
        assertThatThrownBy(() -> tx.execute(s -> service.groupPlaces(group.clusterId(),1,20)))
                .isInstanceOf(AdminException.class).hasMessageContaining("변경");
    }

    @Test
    void denseResultReaggregatesWithoutLossAndPanKeepsGridOrigin() {
        jdbc.update("""
                INSERT INTO map_place
                SELECT i,'카페','cafe','대구','CAFE',7,35.81+(i%%100)*0.001,
                    128.51+(i/100)*0.001,
                    ST_SetSRID(ST_MakePoint(128.51+(i/100)*0.001,35.81+(i%%100)*0.001),4326),
                    'HIDDEN','OPERATING',NULL
                FROM generate_series(1,10000) i
                """.replace("%%", "%"));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.typenull.pingdom.moderation.api.place.lookup.AdminPlaceMapController(service)).build();
        long started = System.nanoTime();
        var response = tx.execute(s -> {
            try {
                var http = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/places/map")
                        .param("west","128.2").param("south","35.5").param("east","128.95").param("north","36.4")
                        .param("zoom","20").param("width","1200").param("height","800")).andReturn();
                assertThat(http.getResponse().getStatus()).isEqualTo(200);
                return new ObjectMapper().readValue(http.getResponse().getContentAsByteArray(),
                        com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.class);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("ADMIN_MAP_DAEGU_SYNTHETIC_10000_HTTP total_ms=" + (System.nanoTime()-started)/1_000_000.0
                + " clusters="+response.clusters().size()+" markers="+response.markers().size());
        jdbc.execute("ANALYZE map_place");
        String measuredSql = recording.spatialSql;
        var measuredParams = recording.spatialParams;
        var plan = tx.execute(s -> recording.query("EXPLAIN (ANALYZE, BUFFERS) " + measuredSql,
                measuredParams, (rs, row) -> rs.getString(1)));
        System.out.println("ADMIN_MAP_ACTUAL_CLUSTER_PLAN\n"+String.join("\n",plan));
        assertThat(response.complete()).isTrue();
        assertThat(response.clusters().size()+response.markers().size()).isLessThanOrEqualTo(200);
        assertThat(response.clusters().stream().mapToLong(c->c.count()).sum()+response.markers().size()).isEqualTo(10000);
        var c = AdminPlaceMapCondition.from(request(14,null));
        var shifted = new AdminPlaceMapCondition(128.49,35.79,128.71,35.96,"",null,null,null);
        var before = tx.execute(s -> repository.findCells(c,1000,false,201));
        var after = tx.execute(s -> repository.findCells(shifted,1000,false,201));
        assertThat(before.stream().map(x -> List.of(x.x(),x.y())).toList())
                .isEqualTo(after.stream().map(x -> List.of(x.x(),x.y())).toList());
    }

    @Test
    void excludesInvalidOrInconsistentCoordinatesAndHandlesEmptyViewport() {
        place(1,128.6,35.87,null,"VISIBLE");
        jdbc.update("UPDATE map_place SET longitude=129 WHERE map_place_id=1");
        var response = tx.execute(s -> service.find(request(14,null)));
        assertThat(response.viewportPlaceCount()).isZero();
        assertThat(response.markers()).isEmpty();
    }

    @Test
    void groupFingerprintChangesForMetadataAndFilterStatusUpdates() {
        place(1,128.6,35.87,null,"HIDDEN");
        place(2,128.6,35.87,null,"HIDDEN");
        var response = tx.execute(s -> service.find(request(20,PlaceDiscoveryStatus.HIDDEN)));
        String token = response.clusters().getFirst().clusterId();
        jdbc.update("UPDATE map_place SET place_name='변경된 이름' WHERE map_place_id=1");
        assertThatThrownBy(() -> tx.execute(s -> service.groupPlaces(token,1,20))).isInstanceOf(AdminException.class);
        var fresh = tx.execute(s -> service.find(request(20,PlaceDiscoveryStatus.HIDDEN)));
        String next = fresh.clusters().getFirst().clusterId();
        jdbc.update("UPDATE map_place SET discovery_status='VISIBLE' WHERE map_place_id=1");
        assertThatThrownBy(() -> tx.execute(s -> service.groupPlaces(next,1,20))).isInstanceOf(AdminException.class);
    }
    @Test
    void postgresStatementTimeoutIsTranslatedIntoPublicDomainError() {
        var slow = new AdminPlaceMapQueryRepository(recording) {
            @Override
            public long countViewport(AdminPlaceMapCondition condition) {
                return jdbc.queryForObject("SELECT 1 FROM pg_sleep(4)", Long.class);
            }
        };
        var slowService = new AdminPlaceMapQueryService(slow, new AdminPlaceMapClusterToken(new ObjectMapper(),
                new JwtProperties("test-map-".repeat(8),60,600)),
                new com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapProperties(200));
        assertThatThrownBy(() -> tx.execute(s -> slowService.find(request(14,null))))
                .isInstanceOf(AdminException.class).hasMessageContaining("초과");
    }

    private static class RecordingJdbc extends NamedParameterJdbcTemplate {
        String spatialSql;
        org.springframework.jdbc.core.namedparam.SqlParameterSource spatialParams;
        RecordingJdbc(javax.sql.DataSource ds) { super(ds); }
        @Override
        public <T> List<T> query(String sql, org.springframework.jdbc.core.namedparam.SqlParameterSource params,
                org.springframework.jdbc.core.RowMapper<T> mapper) {
            if (sql.contains("AS grid_x") && !sql.startsWith("EXPLAIN")) {
                spatialSql = sql;
                spatialParams = params;
            }
            return super.query(sql, params, mapper);
        }
    }

}
