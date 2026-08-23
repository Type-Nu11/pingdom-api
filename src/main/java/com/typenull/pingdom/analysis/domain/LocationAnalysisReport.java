package com.typenull.pingdom.analysis.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PDF와 생성 당시 입력·HTML을 함께 보관하는 입지 분석 보고서입니다. */
@Entity
@Getter
@Table(name = "location_analysis_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationAnalysisReport {

    @Id
    @Column(name = "report_id", length = 36, nullable = false, updatable = false)
    private String reportId;

    @Column(name = "report_name", length = 200, nullable = false)
    private String reportName;

    @Column(nullable = false, length = 200)
    private String category;

    @Column(nullable = false, length = 300)
    private String region;

    @Column(name = "target_customer_group", length = 200)
    private String targetCustomerGroup;

    @Column(name = "operating_hours", length = 200)
    private String operatingHours;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "privacy_consent", nullable = false)
    private boolean privacyConsent;

    @Column(name = "published_date", nullable = false)
    private LocalDate publishedDate;

    @Column(name = "analysis_basis_date", nullable = false)
    private LocalDate analysisBasisDate;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "html_content", columnDefinition = "text", nullable = false)
    private String htmlContent;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "pdf_content", columnDefinition = "bytea", nullable = false)
    private byte[] pdfContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private LocationAnalysisReport(
            String reportId,
            String reportName,
            String category,
            String region,
            String targetCustomerGroup,
            String operatingHours,
            String email,
            boolean privacyConsent,
            LocalDate publishedDate,
            LocalDate analysisBasisDate,
            String htmlContent,
            byte[] pdfContent,
            LocalDateTime now
    ) {
        this.reportId = reportId;
        this.reportName = reportName;
        this.category = category;
        this.region = region;
        this.targetCustomerGroup = targetCustomerGroup;
        this.operatingHours = operatingHours;
        this.email = email;
        this.privacyConsent = privacyConsent;
        this.publishedDate = publishedDate;
        this.analysisBasisDate = analysisBasisDate;
        this.htmlContent = htmlContent;
        this.pdfContent = pdfContent;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static LocationAnalysisReport create(
            String reportId,
            String reportName,
            String category,
            String region,
            String targetCustomerGroup,
            String operatingHours,
            String email,
            boolean privacyConsent,
            LocalDate publishedDate,
            LocalDate analysisBasisDate,
            String htmlContent,
            byte[] pdfContent,
            LocalDateTime now
    ) {
        return new LocationAnalysisReport(reportId, reportName, category, region, targetCustomerGroup,
                operatingHours, email, privacyConsent, publishedDate, analysisBasisDate, htmlContent, pdfContent, now);
    }

    public void update(String reportName, String email, LocalDateTime now) {
        this.reportName = reportName;
        this.email = email;
        this.updatedAt = now;
    }
}
