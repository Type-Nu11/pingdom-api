package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceTrendQueryRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 이전 SQL과 현재 SQL을 같은 PostgreSQL에서 비교하는 테스트 전용 도구. */
final class PlaceTrendHistoryCostProbe {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate snapshot;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String legacyList;
    private final String legacyCount;
    private final String current;

    PlaceTrendHistoryCostProbe(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) throws Exception {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        String[] legacy = new ClassPathResource("performance/1846-legacy-trend.sql")
                .getContentAsString(StandardCharsets.UTF_8).split("-- COUNT_QUERY", 2);
        legacyList = legacy[0];
        legacyCount = legacy[1];
        current = PlaceTrendQueryRepository.class.getMethod("findTrendPage", LocalDateTime.class,
                LocalDateTime.class, Long.class, long.class, int.class).getAnnotation(Query.class).value();
    }

    void assertEquivalent(LocalDateTime start, LocalDateTime end, long userId, long offset, int limit,
                          List<PlaceTrendQueryRepository.PlaceTrendProjection> actual) {
        Page expected = legacy(parameters(start, end, userId, offset, limit));
        assertThat(actual).isNotEmpty();
        assertThat(actual).allSatisfy(row -> assertThat(row.getTotalElements()).isEqualTo(expected.total()));
        assertThat(actual.stream().filter(row -> row.getPlaceId() != null).map(Row::from).toList())
                .containsExactlyElementsOf(expected.rows());
    }

    void measure(LocalDateTime start, LocalDateTime end, List<Long> places) throws Exception {
        Path output = Path.of(System.getenv("TREND_QUERY_EVIDENCE_DIR"));
        Files.createDirectories(output);
        var measurements = new ArrayList<Map<String, Object>>();
        for (int history : new int[]{10_000, 100_000, 1_000_000}) {
            seedHistory(start, places, history);
            var params = parameters(start, end, 1L, 0L, 20);
            Page oldPage = legacy(params);
            assertThat(current(params)).isEqualTo(oldPage);
            assertThat(oldPage.total()).isEqualTo(20L);
            assertThat(oldPage.rows()).allSatisfy(row -> assertThat(row.bookmarkAdds()).isEqualTo(50L));
            assertThat(current(parameters(start, end, 1L, 40L, 20)))
                    .isEqualTo(legacy(parameters(start, end, 1L, 40L, 20)));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM map_bookmark_trend_event WHERE occurred_at >= ?",
                    Long.class, start)).isEqualTo(1_000L);
            for (int i = 0; i < 2; i++) {
                legacy(params);
                current(params);
            }
            var oldMs = new ArrayList<Double>();
            var newMs = new ArrayList<Double>();
            for (int i = 0; i < 7; i++) {
                // 항상 먼저 실행한 쿼리만 캐시 비용을 부담하지 않도록 순서를 번갈아 준다.
                if (i % 2 == 0) {
                    oldMs.add(elapsed(() -> legacy(params)));
                    newMs.add(elapsed(() -> current(params)));
                } else {
                    newMs.add(elapsed(() -> current(params)));
                    oldMs.add(elapsed(() -> legacy(params)));
                }
            }
            JsonNode oldCountPlan = explain(legacyCount, params);
            JsonNode oldListPlan = explain(legacyList, params);
            JsonNode newPlan = explain(current, params);
            write(output.resolve(history + "-legacy-count-plan.json"), oldCountPlan);
            write(output.resolve(history + "-legacy-list-plan.json"), oldListPlan);
            write(output.resolve(history + "-current-plan.json"), newPlan);
            var item = new LinkedHashMap<String, Object>();
            item.put("historyRows", history);
            item.put("recentRows", 1_000);
            item.put("legacy", timing(oldMs, List.of(oldCountPlan, oldListPlan)));
            item.put("current", timing(newMs, List.of(newPlan)));
            measurements.add(item);
            System.out.println("TREND_QUERY_COST " + mapper.writeValueAsString(item));
        }
        write(output.resolve("measurement.json"), Map.of(
                "baseline", "81737090f002986fa5545c59efe87d2d15aa0c53",
                "postgres", jdbc.queryForObject("SHOW server_version", String.class),
                "workMem", jdbc.queryForObject("SHOW work_mem", String.class),
                "warmupPerFlow", 2, "samplesPerFlow", 7,
                "measurements", measurements));
    }

    private void seedHistory(LocalDateTime start, List<Long> places, int history) {
        jdbc.execute("TRUNCATE map_bookmark_trend_event");
        // 모든 규모에서 같은 1,000개 stream을 짝수 번 토글해 최종 상태를 REMOVED로 고정한다.
        for (int index = 0; index < places.size(); index++) {
            jdbc.update("""
                    INSERT INTO map_bookmark_trend_event (user_id, place_id, origin_place_id, event_type, occurred_at)
                    SELECT 10000 + stream, ?, ?, CASE WHEN step % 2 = 0 THEN 'REMOVED' ELSE 'ADDED' END,
                           CAST(? AS timestamp) - INTERVAL '2 days' + step * INTERVAL '1 second'
                    FROM generate_series(?, ?, 20) stream CROSS JOIN generate_series(1, ?) step
                    """, places.get(index), places.get(index), start, index + 1, 1_000, history / 1_000);
            jdbc.update("""
                    INSERT INTO map_bookmark_trend_event (user_id, place_id, origin_place_id, event_type, occurred_at)
                    SELECT stream, ?, ?, 'ADDED', CAST(? AS timestamp) + INTERVAL '1 day'
                    FROM generate_series(?, ?, 20) stream
                    """, places.get(index), places.get(index), start, index + 1, 1_000);
        }
        jdbc.execute("ANALYZE map_bookmark_trend_event");
        jdbc.execute("ANALYZE map_place");
        jdbc.execute("ANALYZE map_bookmark");
        jdbc.execute("ANALYZE map_image");
    }

    private Page legacy(MapSqlParameterSource params) {
        return snapshot.execute(status -> new Page(named.queryForObject(legacyCount, params, Long.class),
                named.query(legacyList, params, (row, index) -> Row.from(row))));
    }

    private Page current(MapSqlParameterSource params) {
        return snapshot.execute(status -> {
            var rows = named.query(current, params, (row, index) -> new PageRow(row.getLong("totalelements"),
                    row.getObject("placeid") == null ? null : Row.from(row)));
            var items = rows.stream().map(PageRow::item).filter(java.util.Objects::nonNull).toList();
            return new Page(rows.getFirst().total(), items);
        });
    }

    private MapSqlParameterSource parameters(LocalDateTime start, LocalDateTime end, long userId,
                                              long offset, int limit) {
        return new MapSqlParameterSource().addValue("periodStart", start).addValue("periodEnd", end)
                .addValue("userId", userId).addValue("offset", offset).addValue("limit", limit);
    }

    private JsonNode explain(String sql, MapSqlParameterSource params) throws IOException {
        return mapper.readTree(named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql,
                params, String.class));
    }

    private Map<String, Object> timing(List<Double> samples, List<JsonNode> plans) {
        var sorted = samples.stream().sorted().toList();
        var output = new LinkedHashMap<String, Object>();
        output.put("samplesMs", samples);
        output.put("p50Ms", sorted.get((int) Math.ceil(sorted.size() * .50) - 1));
        output.put("p95Ms", sorted.get((int) Math.ceil(sorted.size() * .95) - 1));
        output.put("explainExecutionMs", plans.stream().mapToDouble(plan -> plan.get(0).path("Execution Time").asDouble()).sum());
        for (String metric : List.of("Shared Hit Blocks", "Shared Read Blocks", "Temp Read Blocks", "Temp Written Blocks")) {
            output.put(metric, plans.stream().mapToLong(plan -> plan.get(0).path("Plan").path(metric).asLong()).sum());
        }
        output.put("eventRowsVisited", plans.stream().mapToLong(plan -> eventRows(plan.get(0).path("Plan"))).sum());
        return output;
    }

    private long eventRows(JsonNode plan) {
        long rows = "map_bookmark_trend_event".equals(plan.path("Relation Name").asText())
                ? (plan.path("Actual Rows").asLong() + plan.path("Rows Removed by Filter").asLong())
                * plan.path("Actual Loops").asLong() : 0;
        for (JsonNode child : plan.path("Plans")) {
            rows += eventRows(child);
        }
        return rows;
    }

    private double elapsed(Supplier<Page> action) {
        long started = System.nanoTime();
        action.get();
        return (System.nanoTime() - started) / 1_000_000d;
    }

    private void write(Path file, Object value) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }

    private record Page(long total, List<Row> rows) {}
    private record PageRow(long total, Row item) {}

    private record Row(Long placeId, String placeName, String category, String address, String imageUrl,
                       long bookmarkAdds, long bookmarkRemoves, long netBookmarkGrowth,
                       long bookmarkCount, boolean bookmarked) {
        private static Row from(ResultSet row) throws SQLException {
            return new Row(row.getLong("placeid"), row.getString("placename"), row.getString("category"),
                    row.getString("address"), row.getString("imageurl"), row.getLong("bookmarkadds"),
                    row.getLong("bookmarkremoves"), row.getLong("netbookmarkgrowth"),
                    row.getLong("bookmarkcount"), row.getBoolean("bookmarked"));
        }

        private static Row from(PlaceTrendQueryRepository.PlaceTrendProjection row) {
            return new Row(row.getPlaceId(), row.getPlaceName(), row.getCategory(), row.getAddress(),
                    row.getImageUrl(), row.getBookmarkAdds(), row.getBookmarkRemoves(),
                    row.getNetBookmarkGrowth(), row.getBookmarkCount(), row.getBookmarked());
        }
    }
}
