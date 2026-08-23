package com.typenull.pingdom.analysis.infrastructure;

import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationAnalysisReportRepository extends JpaRepository<LocationAnalysisReport, String> {

    @Query("""
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.category AS category, report.region AS region,
                   report.targetCustomerGroup AS targetCustomerGroup,
                   report.operatingHours AS operatingHours, report.email AS email,
                   report.privacyConsent AS privacyConsent, report.publishedDate AS publishedDate,
                   report.analysisBasisDate AS analysisBasisDate, report.createdAt AS createdAt
            FROM LocationAnalysisReport report
            WHERE report.email = :email
            ORDER BY report.createdAt DESC
            """)
    List<SummaryView> findSummariesByEmail(@Param("email") String email);

    Optional<LocationAnalysisReport> findByReportIdAndEmail(String reportId, String email);

    @Query("""
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.category AS category, report.region AS region,
                   report.targetCustomerGroup AS targetCustomerGroup,
                   report.operatingHours AS operatingHours, report.email AS email,
                   report.privacyConsent AS privacyConsent, report.publishedDate AS publishedDate,
                   report.analysisBasisDate AS analysisBasisDate, report.createdAt AS createdAt
            FROM LocationAnalysisReport report
            WHERE report.reportId = :reportId AND report.email = :email
            """)
    Optional<SummaryView> findSummaryByReportIdAndEmail(
            @Param("reportId") String reportId,
            @Param("email") String email
    );

    @Query("SELECT report.htmlContent FROM LocationAnalysisReport report "
            + "WHERE report.reportId = :reportId AND report.email = :email")
    Optional<String> findHtmlByReportIdAndEmail(
            @Param("reportId") String reportId,
            @Param("email") String email
    );

    interface SummaryView {
        String getReportId();

        String getReportName();

        String getCategory();

        String getRegion();

        String getTargetCustomerGroup();

        String getOperatingHours();

        String getEmail();

        boolean isPrivacyConsent();

        java.time.LocalDate getPublishedDate();

        java.time.LocalDate getAnalysisBasisDate();

        java.time.LocalDateTime getCreatedAt();
    }
}
