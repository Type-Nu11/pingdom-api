package com.typenull.pingdom.analysis.infrastructure;

import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 생성 당시 사용자 ID로 보관 보고서를 조회. 이메일은 연락처이며 조회 권한 조건으로 사용하지 않음.
 * 소유자 ID가 없는 기존 보고서는 사용자 조회에서 제외. 목록·요약 projection과 HTML 단독 조회는 본문 로딩을 제한.
 */
public interface LocationAnalysisReportRepository extends JpaRepository<LocationAnalysisReport, String> {

    @Query(value = """
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.category AS category, report.region AS region,
                   report.targetCustomerGroup AS targetCustomerGroup,
                   report.operatingHours AS operatingHours, report.email AS email,
                   report.privacyConsent AS privacyConsent, report.publishedDate AS publishedDate,
                   report.analysisBasisDate AS analysisBasisDate, report.createdAt AS createdAt
            FROM LocationAnalysisReport report
            WHERE report.ownerUserId = :ownerUserId
            ORDER BY report.createdAt DESC, report.reportId DESC
            """, countQuery = """
            SELECT COUNT(report)
            FROM LocationAnalysisReport report
            WHERE report.ownerUserId = :ownerUserId
            """)
    Page<SummaryView> findSummariesByOwnerUserId(@Param("ownerUserId") long ownerUserId, Pageable pageable);

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

    @Query("""
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.category AS category, report.region AS region,
                   report.targetCustomerGroup AS targetCustomerGroup,
                   report.operatingHours AS operatingHours, report.email AS email,
                   report.privacyConsent AS privacyConsent, report.publishedDate AS publishedDate,
                   report.analysisBasisDate AS analysisBasisDate, report.createdAt AS createdAt,
                   report.updatedAt AS updatedAt, report.version AS version
            FROM LocationAnalysisReport report
            WHERE report.reportId = :reportId AND report.ownerUserId = :ownerUserId
            """)
    Optional<MetadataView> findMetadataByReportIdAndOwnerUserId(
            @Param("reportId") String reportId,
            @Param("ownerUserId") long ownerUserId
    );

    // bulk 명령 뒤 관리 중인 보고서가 이전 version·상태로 남지 않도록 먼저 flush하고 비웁니다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE LocationAnalysisReport report
            SET report.reportName = :reportName, report.email = :email,
                report.updatedAt = :updatedAt, report.version = report.version + 1
            WHERE report.reportId = :reportId AND report.ownerUserId = :ownerUserId
              AND report.version = :version
            """)
    int updateMetadataIfVersionMatches(
            @Param("reportId") String reportId,
            @Param("ownerUserId") long ownerUserId,
            @Param("version") long version,
            @Param("reportName") String reportName,
            @Param("email") String email,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM LocationAnalysisReport report
            WHERE report.reportId = :reportId AND report.ownerUserId = :ownerUserId
              AND report.version = :version
            """)
    int deleteIfVersionMatches(
            @Param("reportId") String reportId,
            @Param("ownerUserId") long ownerUserId,
            @Param("version") long version
    );

    @Query("""
            SELECT report.reportId AS reportId, report.reportName AS reportName,
                   report.publishedDate AS publishedDate, report.version AS version,
                   report.pdfContent AS pdfContent
            FROM LocationAnalysisReport report
            WHERE report.reportId = :reportId AND report.ownerUserId = :ownerUserId
            """)
    Optional<PdfView> findPdfByReportIdAndOwnerUserId(
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

    interface MetadataView extends SummaryView {
        LocalDateTime getUpdatedAt();

        long getVersion();
    }

    interface PdfView {
        String getReportId();

        String getReportName();

        java.time.LocalDate getPublishedDate();

        long getVersion();

        byte[] getPdfContent();
    }
}
