package com.typenull.pingdom.place.infrastructure.persistence.place;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.DisplayStatus;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Marker;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapClusterToken;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapCondition;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapProperties;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapQueryService;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.shared.security.jwt.JwtProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 실제 PostGIS와 서비스의 트랜잭션 프록시로 영역 집계·마커·동시 변경 계약을 검증한다. */
@Tag("postgres-integration")
@Tag("postgres-smoke")
@Testcontainers
class AdminPlaceMapQueryRepositoryPostgreSqlIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private AdminPlaceMapQueryRepository repository;
    private AdminPlaceMapQueryService service;
    private AdminPlaceMapClusterToken tokens;
    private TransactionTemplate tx;
    private RecordingJdbc recording;

    @BeforeEach
    void setup() {
        dataSource = newDataSource();
        jdbc = new JdbcTemplate(dataSource);
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
        recording = new RecordingJdbc(dataSource);
        repository = new AdminPlaceMapQueryRepository(recording);
        tokens = new AdminPlaceMapClusterToken(new ObjectMapper(), new JwtProperties("test-map-".repeat(8), 60, 600));
        service = transactionalService(repository);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.setReadOnly(true);
    }

    private DataSource newDataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private AdminPlaceMapQueryService transactionalService(AdminPlaceMapQueryRepository targetRepository) {
        var target = new AdminPlaceMapQueryService(targetRepository, tokens, new AdminPlaceMapProperties(100));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        return (AdminPlaceMapQueryService) proxy.getProxy();
    }

    private void place(long id, double lon, double lat, String region, String visibility) {
        jdbc.update("""
                INSERT INTO map_place VALUES (?, '카페', 'cafe', '대구 수성구', 'coffee', 7, ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?),4326), ?, 'OPERATING', ?)
                """, id, lat, lon, lon, lat, visibility, region);
    }

    private void colocatedPlaces(int count) {
        jdbc.update("""
                INSERT INTO map_place
                SELECT i,'카페','cafe','대구','CAFE',7,35.87,128.6,
                    ST_SetSRID(ST_MakePoint(128.6,35.87),4326),'HIDDEN','OPERATING',NULL
                FROM generate_series(1,?) i
                """, count);
    }

    private AdminPlaceMapRequest request(int zoom, PlaceDiscoveryStatus status) {
        return new AdminPlaceMapRequest(128.5, 35.8, 128.7, 35.95, zoom, 1200, 800, null, null, status, null);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "0,99", "0,100", "0,101", "0,10000",
            "20,0", "20,1", "20,99", "20,100", "20,101", "20,10000"})
    void appliesExactCountBoundaryWithoutDeduplicatingCoordinates(int zoom, int count) {
        colocatedPlaces(count);

        var result = service.find(request(zoom, null));

        assertThat(result.viewportPlaceCount()).isEqualTo(count);
        assertThat(result.displayLimit()).isEqualTo(100);
        assertThat(result.regions()).isEmpty();
        assertThat(result.clusters()).isEmpty();
        if (count <= 100) {
            assertThat(result.displayStatus()).isEqualTo(DisplayStatus.MARKERS);
            assertThat(result.complete()).isTrue();
            assertThat(result.markers()).extracting(Marker::id)
                    .containsExactlyElementsOf(LongStream.rangeClosed(1, count).boxed().toList());
            assertThat(result.markers()).extracting(Marker::id).doesNotHaveDuplicates();
        } else {
            assertThat(result.displayStatus()).isEqualTo(DisplayStatus.ZOOM_REQUIRED);
            assertThat(result.complete()).isFalse();
            assertThat(result.markers()).isEmpty();
            assertThat(result.recoveryAction()).isEqualTo("ZOOM_IN");
        }
        assertThat(recording.reads).hasSize(count > 0 && count <= 100 ? 2 : 1);
        assertThat(recording.reads.getFirst()).startsWith("SELECT COUNT(*) FROM map_place");
        assertThat(recording.reads).noneMatch(sql -> sql.contains("GROUP BY") || sql.contains("WITH points"));
    }

    @Test
    void includesUnlinkedRegionsAndOnlyCountsViewportPlacesAtEveryZoom() {
        place(1, 128.6, 35.87, "27260", "VISIBLE");
        place(2, 128.9, 35.87, "27260", "HIDDEN");
        place(3, 128.61, 35.88, null, "HIDDEN");
        place(4, 128.62, 35.89, "99999", "VISIBLE");
        jdbc.update("INSERT INTO map_place(map_place_id,place_name,region_code,discovery_status,operating_status) VALUES (5,'좌표없음','27260','HIDDEN','OPERATING')");

        for (int zoom : List.of(0, 7, 10, 13, 20)) {
            var result = service.find(request(zoom, null));
            assertThat(result.viewportPlaceCount()).isEqualTo(3);
            assertThat(result.markers()).extracting(Marker::id).containsExactly(1L, 3L, 4L);
            assertThat(result.regions()).isEmpty();
            assertThat(result.clusters()).isEmpty();
        }
    }

    @Test
    void defaultsToAllStatusesAndSharesKeywordCategoryAndStatusFilters() {
        place(1, 128.6, 35.87, null, "HIDDEN");
        place(2, 128.61, 35.88, null, "VISIBLE");
        place(3, 128.62, 35.89, null, "HIDDEN");
        place(4, 128.9, 35.87, null, "HIDDEN");
        jdbc.update("UPDATE map_place SET operating_status='PERMANENTLY_CLOSED' WHERE map_place_id=1");
        jdbc.update("UPDATE map_place SET category='restaurant' WHERE map_place_id=3");
        var all = service.find(request(0, null));
        assertThat(all.viewportPlaceCount()).isEqualTo(3);
        assertThat(all.markers()).extracting(Marker::id).containsExactly(1L, 2L, 3L);

        for (String keyword : List.of("7", "카페", "CAFE", "수성구")) {
            var filtered = new AdminPlaceMapRequest(128.5, 35.8, 128.7, 35.95, 0, 64, 64,
                    " " + keyword + " ", "커피", PlaceDiscoveryStatus.HIDDEN, PlaceOperatingStatus.PERMANENTLY_CLOSED);
            var result = service.find(filtered);
            assertThat(result.viewportPlaceCount()).isEqualTo(1);
            assertThat(result.markers()).singleElement().satisfies(m -> {
                assertThat(m.id()).isEqualTo(1);
                assertThat(m.category()).isEqualTo("CAFE");
                assertThat(m.operatingStatus()).isEqualTo("PERMANENTLY_CLOSED");
            });
        }
    }

    @Test
    void countAndMarkersIncludeTheSameBoundingBoxEdges() {
        place(1, 128.5, 35.8, null, "VISIBLE");
        place(2, 128.7, 35.95, null, "VISIBLE");
        place(3, 128.49999, 35.8, null, "VISIBLE");
        place(4, 128.7, 35.95001, null, "VISIBLE");
        var result = service.find(request(0, null));
        assertThat(result.viewportPlaceCount()).isEqualTo(2);
        assertThat(result.markers()).extracting(Marker::id).containsExactly(1L, 2L);
    }

    @Test
    void excludesInvalidEmptyMissingAndInconsistentCoordinates() {
        for (int id = 1; id <= 8; id++) place(id, 128.6, 35.87, null, "VISIBLE");
        jdbc.update("UPDATE map_place SET longitude=129 WHERE map_place_id=2");
        jdbc.update("UPDATE map_place SET latitude=91 WHERE map_place_id=3");
        jdbc.update("UPDATE map_place SET location=NULL WHERE map_place_id=4");
        jdbc.update("UPDATE map_place SET location=ST_GeomFromText('POINT EMPTY',4326) WHERE map_place_id=5");
        jdbc.update("UPDATE map_place SET latitude='NaN'::float8 WHERE map_place_id=6");
        jdbc.update("UPDATE map_place SET longitude='Infinity'::float8 WHERE map_place_id=7");
        jdbc.update("UPDATE map_place SET latitude=NULL WHERE map_place_id=8");
        var result = service.find(request(20, null));
        assertThat(result.viewportPlaceCount()).isEqualTo(1);
        assertThat(result.markers()).extracting(Marker::id).containsExactly(1L);
    }

    @Test
    void keepsCountAndMarkersOnOneSnapshotWhenAnotherConnectionInsertsAfterCount() {
        colocatedPlaces(100);
        var writer = new JdbcTemplate(newDataSource());
        var inserted = new AtomicBoolean();
        var concurrentRepository = new AdminPlaceMapQueryRepository(recording) {
            @Override
            public long countViewport(AdminPlaceMapCondition condition) {
                long count = super.countViewport(condition);
                if (inserted.compareAndSet(false, true)) {
                    writer.update("INSERT INTO map_place SELECT 101,place_name,english_name,address,category,user_id,latitude,longitude,location,discovery_status,operating_status,region_code FROM map_place WHERE map_place_id=1");
                }
                return count;
            }
        };
        var concurrentService = transactionalService(concurrentRepository);

        var current = concurrentService.find(request(20, null));
        var next = concurrentService.find(request(20, null));

        assertThat(current.viewportPlaceCount()).isEqualTo(100);
        assertThat(current.markers()).hasSize(100);
        assertThat(current.markers()).extracting(Marker::id).doesNotContain(101L);
        assertThat(next.viewportPlaceCount()).isEqualTo(101);
        assertThat(next.markers()).isEmpty();
        assertThat(next.displayStatus()).isEqualTo(DisplayStatus.ZOOM_REQUIRED);
    }

    @Test
    void legacyGroupPaginationAndChangedMembershipStillWorkWithPreviouslyIssuedToken() {
        colocatedPlaces(30);
        String token = legacyToken(null);
        var page = service.groupPlaces(token, 2, 20);
        assertThat(page.places()).hasSize(10);
        assertThat(page.places().getFirst().id()).isEqualTo(21);
        assertThat(page.totalCount()).isEqualTo(30);
        jdbc.update("UPDATE map_place SET longitude=128.61, location=ST_SetSRID(ST_MakePoint(128.61,latitude),4326) WHERE map_place_id=30");
        assertThatThrownBy(() -> service.groupPlaces(token, 1, 20))
                .isInstanceOf(AdminException.class).hasMessageContaining("변경");
    }

    @Test
    void legacyGroupFingerprintStillRejectsMetadataAndFilterStatusChanges() {
        colocatedPlaces(2);
        String token = legacyToken(PlaceDiscoveryStatus.HIDDEN);
        jdbc.update("UPDATE map_place SET place_name='변경된 이름' WHERE map_place_id=1");
        assertThatThrownBy(() -> service.groupPlaces(token, 1, 20)).isInstanceOf(AdminException.class);
        String freshToken = legacyToken(PlaceDiscoveryStatus.HIDDEN);
        jdbc.update("UPDATE map_place SET discovery_status='VISIBLE' WHERE map_place_id=1");
        assertThatThrownBy(() -> service.groupPlaces(freshToken, 1, 20)).isInstanceOf(AdminException.class);
    }

    private String legacyToken(PlaceDiscoveryStatus status) {
        var condition = AdminPlaceMapCondition.from(request(20, status));
        var cell = tx.execute(s -> repository.findCells(condition, 1000, false, 201)).getFirst();
        return tokens.issue(condition, 1000, cell.x(), cell.y(), false, cell.fingerprint());
    }

    @Test
    void postgresStatementTimeoutIsTranslatedIntoPublicDomainError() {
        var slow = new AdminPlaceMapQueryRepository(recording) {
            @Override
            public long countViewport(AdminPlaceMapCondition condition) {
                return jdbc.queryForObject("SELECT 1 FROM pg_sleep(4)", Long.class);
            }
        };
        assertThatThrownBy(() -> transactionalService(slow).find(request(14, null)))
                .isInstanceOfSatisfying(AdminException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_PLACE_MAP_QUERY_TIMEOUT));
    }

    @Test
    void syntheticDenseAreaReturnsCountOnlyAndRecordsCountPlanSeparately() throws Exception {
        colocatedPlaces(10000);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.typenull.pingdom.moderation.api.place.lookup.AdminPlaceMapController(service)).build();
        long started = System.nanoTime();
        var http = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/places/map")
                .param("west", "128.5").param("south", "35.8").param("east", "128.7").param("north", "35.95")
                .param("zoom", "20").param("width", "1200").param("height", "800")).andReturn();
        double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;
        assertThat(http.getResponse().getStatus()).isEqualTo(200);
        assertThat(http.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        var result = new ObjectMapper().readValue(http.getResponse().getContentAsByteArray(), AdminPlaceMapResponse.class);
        assertThat(result.viewportPlaceCount()).isEqualTo(10000);
        assertThat(result.markers()).isEmpty();
        assertThat(result.displayStatus()).isEqualTo(DisplayStatus.ZOOM_REQUIRED);
        assertThat(recording.reads).hasSize(1);
        jdbc.execute("ANALYZE map_place");
        var plan = recording.query("EXPLAIN (ANALYZE, BUFFERS) " + recording.countSql,
                recording.countParams, (rs, row) -> rs.getString(1));
        System.out.println("ADMIN_MAP_SYNTHETIC_10000_HTTP total_ms=" + elapsedMillis);
        System.out.println("ADMIN_MAP_SYNTHETIC_COUNT_PLAN\n" + String.join("\n", plan));
    }

    /** 이전 대표 선택용 fixture도 전체 마커로 반환하고 기존 그룹 토큰은 계속 검증한다. */
    @Test
    void returnsAllFormerRepresentativeCandidatesAcrossZoomPanAndPlaceChanges() {
        double cell = 40075016.68557849 / Math.scalb(1d, 12) / 4;
        double origin = Math.floor((128.6 / 360 * 40075016.68557849) / cell) * cell;
        double first = (origin + cell * 0.2) / 40075016.68557849 * 360;
        double second = (origin + cell * 0.3) / 40075016.68557849 * 360;
        double third = (origin + cell * 0.8) / 40075016.68557849 * 360;
        place(1,129.2,35.87,null,"VISIBLE");
        place(2,first,35.87,null,"HIDDEN");
        place(3,first,35.87,null,"HIDDEN");
        place(10,first,35.87,null,"VISIBLE");
        place(20,second,35.87,null,"VISIBLE");
        place(30,third,35.87,null,"VISIBLE");
        var parent = tx.execute(s -> service.find(request(12,PlaceDiscoveryStatus.VISIBLE)));
        assertThat(parent.viewportPlaceCount()).isEqualTo(3);
        assertThat(parent.clusters()).isEmpty();
        assertThat(parent.markers()).extracting(Marker::id).containsExactly(10L, 20L, 30L);
        var condition = AdminPlaceMapCondition.from(request(12, PlaceDiscoveryStatus.VISIBLE));
        var group = tx.execute(s -> repository.findCells(condition, cell, false, 201)).getFirst();
        String legacyToken = tokens.issue(condition, cell, group.x(), group.y(), false, group.fingerprint());
        var repeated = tx.execute(s -> service.find(request(12,PlaceDiscoveryStatus.VISIBLE)));
        assertThat(repeated.markers()).containsExactlyElementsOf(parent.markers());
        var child = tx.execute(s -> service.find(request(13,PlaceDiscoveryStatus.VISIBLE)));
        assertThat(child.clusters()).isEmpty();
        assertThat(child.markers()).containsExactlyElementsOf(parent.markers());
        var list = tx.execute(s -> service.groupPlaces(legacyToken,1,20));
        assertThat(list.places()).extracting(Marker::id).containsExactly(10L, 20L, 30L);
        var hidden = tx.execute(s -> service.find(request(12,PlaceDiscoveryStatus.HIDDEN)));
        assertThat(hidden.viewportPlaceCount()).isEqualTo(2);
        assertThat(hidden.markers()).extracting(Marker::id).containsExactly(2L, 3L);
        var pannedRequest = new AdminPlaceMapRequest((first+second)/2,35.8,128.7,35.95,12,1200,800,
                null,null,PlaceDiscoveryStatus.VISIBLE,null);
        var panned = tx.execute(s -> service.find(pannedRequest));
        assertThat(panned.viewportPlaceCount()).isEqualTo(2);
        assertThat(panned.markers()).extracting(Marker::id).containsExactly(20L, 30L);
        jdbc.update("DELETE FROM map_place WHERE map_place_id=10");
        var updated = tx.execute(s -> service.find(request(12,PlaceDiscoveryStatus.VISIBLE)));
        assertThat(updated.viewportPlaceCount()).isEqualTo(2);
        assertThat(updated.markers()).extracting(Marker::id).containsExactly(20L, 30L);
        assertThatThrownBy(() -> tx.execute(s -> service.groupPlaces(legacyToken,1,20)))
                .isInstanceOf(AdminException.class);
    }

    private static class RecordingJdbc extends NamedParameterJdbcTemplate {
        final List<String> reads = new ArrayList<>();
        String countSql;
        SqlParameterSource countParams;
        RecordingJdbc(DataSource ds) { super(ds); }

        @Override
        public <T> T queryForObject(String sql, SqlParameterSource params, Class<T> type) {
            reads.add(sql);
            countSql = sql;
            countParams = params;
            return super.queryForObject(sql, params, type);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource params, RowMapper<T> mapper) {
            reads.add(sql);
            return super.query(sql, params, mapper);
        }
    }
}
