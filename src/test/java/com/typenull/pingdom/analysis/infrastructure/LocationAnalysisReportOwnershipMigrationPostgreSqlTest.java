package com.typenull.pingdom.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Tag("migration")
@Testcontainers
class LocationAnalysisReportOwnershipMigrationPostgreSqlTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void quarantinesLegacyReportsWithoutGuessingOwnerFromEmail() throws Exception {
        try (Connection connection = postgres.createConnection(""); Statement statement = connection.createStatement()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V108__create_location_analysis_report.sql"));
            statement.executeUpdate("""
                    INSERT INTO location_analysis_report
                        (report_id, report_name, category, region, email, privacy_consent, published_date,
                         analysis_basis_date, html_content, pdf_content, created_at, updated_at)
                    VALUES ('legacy', '기존 보고서', '카페', '서울', 'owner@example.com', true,
                            CURRENT_DATE, CURRENT_DATE, '<html>원문</html>', decode('0102', 'hex'),
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);

            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V142__bind_analysis_reports_to_owner_user_id.sql"));

            try (ResultSet result = statement.executeQuery(
                    "SELECT owner_user_id, html_content, octet_length(pdf_content) FROM location_analysis_report WHERE report_id = 'legacy'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
                assertThat(result.getString(2)).isEqualTo("<html>원문</html>");
                assertThat(result.getInt(3)).isEqualTo(2);
            }
            statement.executeUpdate("UPDATE location_analysis_report SET owner_user_id = 17 WHERE report_id = 'legacy'");
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE location_analysis_report SET owner_user_id = 0 WHERE report_id = 'legacy'"))
                    .isInstanceOf(SQLException.class);
            try (ResultSet result = statement.executeQuery("""
                    SELECT indexdef FROM pg_indexes
                    WHERE tablename = 'location_analysis_report'
                      AND indexname = 'idx_location_analysis_report_owner_created_id'
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).contains("owner_user_id", "created_at DESC", "report_id DESC");
            }
        }
    }
}
