package com.typenull.pingdom.analysis.infrastructure;

import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 생성 당시 사용자 ID로 보관 보고서를 조회. 이메일은 연락처이며 조회 권한 조건으로 사용하지 않음.
 * 소유자 ID가 없는 기존 보고서는 사용자 조회에서 제외. 목록·요약 projection과 HTML 단독 조회는 본문 로딩을 제한.
 */
public interface LocationAnalysisReportRepository extends JpaRepository<LocationAnalysisReport, String> {

    @Query("""
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.category AS category, report.region AS region,
                   report.targetCustomerGroup AS targetCustomerGroup,
                   report.operatingHours AS operatingHours, report.email AS email,
                   report.privacyConsent AS privacyConsent, report.publishedDate AS publishedDate,
                   report.analysisBasisDate AS analysisBasisDate, report.createdAt AS createdAt
            FROM LocationAnalysisReport report
            WHERE report.ownerUserId = :ownerUserId
            ORDER BY report.createdAt DESC
            """)
    List<SummaryView> findSummariesByOwnerUserId(@Param("ownerUserId") long ownerUserId);

    Optional<LocationAnalysisReport> findByReportIdAndOwnerUserId(String reportId, long ownerUserId);

    @Query("""
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.category AS category, report.region AS region,
                   report.targetCustomerGroup AS targetCustomerGroup,
                   report.operatingHours AS operatingHours, report.email AS email,
                   report.privacyConsent AS privacyConsent, report.publishedDate AS publishedDate,
                   report.analysisBasisDate AS analysisBasisDate, report.createdAt AS createdAt
            FROM LocationAnalysisReport report
            WHERE report.reportId = :reportId AND report.ownerUserId = :ownerUserId
            """)
    Optional<SummaryView> findSummaryByReportIdAndOwnerUserId(
            @Param("reportId") String reportId,
            @Param("ownerUserId") long ownerUserId
    );

    @Query("SELECT report.htmlContent FROM LocationAnalysisReport report "
            + "WHERE report.reportId = :reportId AND report.ownerUserId = :ownerUserId")
    Optional<String> findHtmlByReportIdAndOwnerUserId(
            @Param("reportId") String reportId,
            @Param("ownerUserId") long ownerUserId
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
