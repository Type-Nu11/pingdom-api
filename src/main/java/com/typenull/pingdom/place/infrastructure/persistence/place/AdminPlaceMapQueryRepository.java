package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Bounds;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Marker;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Region;
import com.typenull.pingdom.moderation.application.query.place.map.AdminPlaceMapCondition;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 기존 viewport와 같은 PostGIS bbox/SQL 집계를 관리자 범위로 적용한다.
 * 공개 전용 VISIBLE/OPERATING 조건은 복사하지 않는다. 전체 장소를 JVM에 적재하지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class AdminPlaceMapQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String FILTER = """
            (CAST(:keyword AS text) = ''
             OR LOWER(mp.place_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(mp.english_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(mp.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR mp.user_id = :numericKeyword)
            AND (:hasCategory = false OR LOWER(TRIM(mp.category)) IN (:aliases))
            AND (CAST(:discovery AS text) IS NULL OR mp.discovery_status = :discovery)
            AND (CAST(:operating AS text) IS NULL OR mp.operating_status = :operating)
            """;
    // 숫자 좌표와 인덱스 좌표의 불일치를 제외하고 Mercator 변환의 정의역을 보장한다.
    private static final String VALID = """
            mp.latitude BETWEEN -85.05112878 AND 85.05112878
            AND mp.longitude BETWEEN -180 AND 180
            AND mp.location IS NOT NULL AND NOT ST_IsEmpty(mp.location)
            AND ST_X(mp.location) = mp.longitude AND ST_Y(mp.location) = mp.latitude
            """;
    private static final String VIEWPORT = """
            mp.location && ST_MakeEnvelope(:west, :south, :east, :north, 4326)
            """;
    private static final String UNKNOWN_REGION = """
            AND NOT EXISTS (SELECT 1 FROM place_administrative_region r WHERE r.region_code = mp.region_code)
            """;
    // 두 독립 64-bit 지문으로 구성/필드 변경을 감지한다. 토큰 서명과는 별개의 변경 검사용 값이다.
    private static final String FINGERPRINT = """
            CONCAT(COUNT(*), ':', bit_xor(hashtextextended(ROW(map_place_id, place_name, category,
                latitude, longitude, discovery_status, operating_status, region_code)::text, 0)), ':',
                bit_xor(hashtextextended(ROW(map_place_id, place_name, category,
                latitude, longitude, discovery_status, operating_status, region_code)::text, 1)))
            """;
    private static final String CELL = """
            FLOOR(ST_X(ST_Transform(location, 3857)) / :cell) = :gridX
            AND FLOOR(ST_Y(ST_Transform(location, 3857)) / :cell) = :gridY
            """;

    public void setQueryTimeout() {
        // SET LOCAL에 해당한다. 현재 트랜잭션 종료 시 커넥션의 기존 설정으로 복원된다.
        jdbc.getJdbcTemplate().execute("SELECT set_config('statement_timeout', '3000', true)");
    }

    public long countViewport(AdminPlaceMapCondition c) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM map_place mp WHERE " + FILTER + " AND " + VALID
                + " AND " + VIEWPORT, params(c), Long.class);
    }

    public List<Region> findRegions(AdminPlaceMapCondition c, String level, int limit) {
        boolean sido = "SIDO".equals(level);
        String code = sido ? "LEFT(r.region_code, 2)" : "r.region_code";
        String name = sido ? "r.sido" : "r.region_name";
        // 선택은 viewport 내 장소 기준, count는 선택한 지역 전체의 동일 필터 기준이다.
        String sql = """
                WITH selected AS (
                    SELECT DISTINCT %s AS code
                    FROM map_place mp JOIN place_administrative_region r ON r.region_code = mp.region_code
                    WHERE %s AND %s AND %s
                )
                SELECT %s AS code, MIN(%s) AS name, COUNT(*) AS place_count,
                    AVG(CASE WHEN %s THEN mp.latitude END) AS latitude,
                    AVG(CASE WHEN %s THEN mp.longitude END) AS longitude,
                    MIN(CASE WHEN %s THEN mp.longitude END) AS west,
                    MIN(CASE WHEN %s THEN mp.latitude END) AS south,
                    MAX(CASE WHEN %s THEN mp.longitude END) AS east,
                    MAX(CASE WHEN %s THEN mp.latitude END) AS north
                FROM map_place mp JOIN place_administrative_region r ON r.region_code = mp.region_code
                JOIN selected s ON s.code = %s
                WHERE %s
                GROUP BY %s ORDER BY code LIMIT :limit
                """.formatted(code, FILTER, VALID, VIEWPORT, code, name,
                (VALID + " AND " + VIEWPORT), (VALID + " AND " + VIEWPORT), VALID, VALID, VALID, VALID, code, FILTER, code);
        return jdbc.query(sql, params(c).addValue("limit", limit), (rs, row) -> new Region(level,
                rs.getString("code"), rs.getString("name"), rs.getLong("place_count"), "REGION",
                rs.getDouble("latitude"), rs.getDouble("longitude"), bounds(rs), "PLACE_EXTENT", "EXPAND", sido ? 9 : 12));
    }

    private String points(boolean unknownOnly) {
        // MATERIALIZED는 유효성 검증 전 ST_Transform이 실행되는 planner 재배치를 막는다.
        return "WITH points AS MATERIALIZED (SELECT mp.* FROM map_place mp WHERE " + FILTER
                + " AND " + VALID + " AND " + VIEWPORT + (unknownOnly ? UNKNOWN_REGION : "") + ") ";
    }

    public List<Cell> findCells(AdminPlaceMapCondition c, double cellSize, boolean unknownOnly, int limit) {
        String sql = points(unknownOnly) + """
                SELECT FLOOR(ST_X(ST_Transform(location, 3857)) / :cell)::bigint AS grid_x,
                       FLOOR(ST_Y(ST_Transform(location, 3857)) / :cell)::bigint AS grid_y,
                       COUNT(*) AS place_count, MIN(map_place_id) AS single_id,
                       AVG(latitude) AS latitude, AVG(longitude) AS longitude,
                       MIN(longitude) AS west, MIN(latitude) AS south,
                       MAX(longitude) AS east, MAX(latitude) AS north,
                """ + FINGERPRINT + " AS fingerprint FROM points GROUP BY grid_x, grid_y ORDER BY grid_y, grid_x LIMIT :limit";
        return jdbc.query(sql, params(c).addValue("cell", cellSize).addValue("limit", limit),
                (rs, row) -> new Cell(rs.getLong("grid_x"), rs.getLong("grid_y"), rs.getLong("place_count"),
                        rs.getLong("single_id"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                        bounds(rs), rs.getString("fingerprint")));
    }

    public GroupState groupState(AdminPlaceMapCondition c, double cell, long x, long y, boolean unknownOnly) {
        return jdbc.queryForObject(points(unknownOnly) + "SELECT COUNT(*) AS place_count, " + FINGERPRINT
                        + " AS fingerprint FROM points WHERE " + CELL,
                cellParams(c, cell, x, y), (rs, row) -> new GroupState(rs.getLong("place_count"), rs.getString("fingerprint")));
    }

    public List<Marker> groupPlaces(AdminPlaceMapCondition c, double cell, long x, long y,
                                    boolean unknownOnly, int page, int limit) {
        return jdbc.query(points(unknownOnly) + "SELECT * FROM points WHERE " + CELL
                        + " ORDER BY map_place_id LIMIT :limit OFFSET :offset",
                cellParams(c, cell, x, y).addValue("limit", limit).addValue("offset", (long) (page - 1) * limit),
                (rs, row) -> marker(rs));
    }

    public List<Marker> markers(Collection<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.query("SELECT * FROM map_place WHERE map_place_id IN (:ids) ORDER BY map_place_id",
                new MapSqlParameterSource("ids", ids), (rs, row) -> marker(rs));
    }

    private MapSqlParameterSource cellParams(AdminPlaceMapCondition c, double cell, long x, long y) {
        return params(c).addValue("cell", cell).addValue("gridX", x).addValue("gridY", y);
    }

    private MapSqlParameterSource params(AdminPlaceMapCondition c) {
        Long numeric = null;
        try { numeric = Long.valueOf(c.keyword()); } catch (NumberFormatException ignored) { /* 일반 문자열 검색 */ }
        var aliases = PlaceCategoryPolicy.normalizedAliases(c.category());
        return new MapSqlParameterSource()
                .addValue("west", c.west()).addValue("south", c.south()).addValue("east", c.east()).addValue("north", c.north())
                .addValue("keyword", c.keyword()).addValue("numericKeyword", numeric, Types.BIGINT)
                .addValue("hasCategory", c.category() != null).addValue("aliases", aliases.isEmpty() ? List.of("") : aliases)
                .addValue("discovery", c.discoveryStatus() == null ? null : c.discoveryStatus().name(), Types.VARCHAR)
                .addValue("operating", c.operatingStatus() == null ? null : c.operatingStatus().name(), Types.VARCHAR);
    }

    private static Bounds bounds(ResultSet rs) throws SQLException {
        return new Bounds(rs.getDouble("west"), rs.getDouble("south"), rs.getDouble("east"), rs.getDouble("north"));
    }

    private static Marker marker(ResultSet rs) throws SQLException {
        return new Marker(rs.getLong("map_place_id"), rs.getString("place_name"), rs.getDouble("latitude"),
                rs.getDouble("longitude"), PlaceCategoryPolicy.canonicalOrNull(rs.getString("category")),
                rs.getString("discovery_status"), rs.getString("operating_status"));
    }

    public record Cell(long x, long y, long count, long singleId, double latitude, double longitude,
                       Bounds bounds, String fingerprint) {}
    public record GroupState(long count, String fingerprint) {}
}
