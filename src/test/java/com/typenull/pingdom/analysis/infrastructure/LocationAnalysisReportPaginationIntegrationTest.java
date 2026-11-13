package com.typenull.pingdom.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@Tag("integration")
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportPaginationIntegrationTest$SqlCapture"
})
@ContextConfiguration(classes = LocationAnalysisReportPaginationIntegrationTest.Config.class)
class LocationAnalysisReportPaginationIntegrationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Autowired private LocationAnalysisReportRepository repository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LocationAnalysisReport.class)
    @EnableJpaRepositories(basePackageClasses = LocationAnalysisReportRepository.class)
    static class Config {
    }

    @AfterEach
    void clearCapture() {
        SqlCapture.clear();
    }

    @Test
    void returnsEmptyAndSingleSummaryPages() {
        assertThat(repository.findSummariesByOwnerUserId(7L, PageRequest.of(0, 20)).getTotalElements()).isZero();
        entityManager.persist(report("single", 7L));
        entityManager.flush();
        entityManager.clear();
        SqlCapture.reset();

        var page = repository.findSummariesByOwnerUserId(7L, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(LocationAnalysisReportRepository.SummaryView::getReportId)
                .containsExactly("single");
        assertThat(page.hasNext()).isFalse();
        assertThat(SqlCapture.statements()).noneMatch(sql -> sql.contains("pdf_content") || sql.contains("html_content"));
    }

    @Test
    void boundsRowsAndStablyPagesSameTimestampWithinOwnerScopeWithoutLoadingPayload() {
        for (int index = 0; index <= 100; index++) {
            entityManager.persist(report("report-%03d".formatted(index), 7L));
        }
        entityManager.persist(report("other-owner", 8L));
        entityManager.persist(report("legacy-null-owner", 7L));
        entityManager.flush();
        jdbcTemplate.update("UPDATE location_analysis_report SET owner_user_id = NULL WHERE report_id = ?",
                "legacy-null-owner");
        entityManager.clear();
        SqlCapture.reset();

        var first = repository.findSummariesByOwnerUserId(7L, PageRequest.of(0, 100));
        var last = repository.findSummariesByOwnerUserId(7L, PageRequest.of(1, 100));
        var beyond = repository.findSummariesByOwnerUserId(7L, PageRequest.of(3, 100));

        assertThat(first.getContent()).hasSize(100);
        assertThat(first.getTotalElements()).isEqualTo(101);
        assertThat(first.hasNext()).isTrue();
        assertThat(last.getContent()).hasSize(1);
        assertThat(last.getTotalElements()).isEqualTo(101);
        assertThat(last.hasNext()).isFalse();
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(101);
        var actualIds = Stream.concat(first.stream(), last.stream())
                .map(LocationAnalysisReportRepository.SummaryView::getReportId).toList();
        var expectedIds = IntStream.rangeClosed(0, 100).mapToObj(index -> "report-%03d".formatted(index))
                .sorted(Comparator.reverseOrder()).toList();
        assertThat(actualIds).containsExactlyElementsOf(expectedIds).doesNotHaveDuplicates();
        assertThat(SqlCapture.statements()).isNotEmpty()
                .noneMatch(sql -> sql.contains("pdf_content") || sql.contains("html_content"));
    }

    private LocationAnalysisReport report(String reportId, long ownerUserId) {
        return LocationAnalysisReport.create(reportId, ownerUserId, "분석", "카페", "서울", null, null,
                "owner@example.com", true, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11),
                "<html>목록에 포함하지 않는 본문</html>", new byte[]{1, 2, 3}, CREATED_AT);
    }

    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> STATEMENTS = ThreadLocal.withInitial(ArrayList::new);

        @Override
        public String inspect(String sql) {
            STATEMENTS.get().add(sql.toLowerCase(java.util.Locale.ROOT));
            return sql;
        }

        static void reset() {
            STATEMENTS.get().clear();
        }

        static List<String> statements() {
            return List.copyOf(STATEMENTS.get());
        }

        static void clear() {
            STATEMENTS.remove();
        }
    }
}
