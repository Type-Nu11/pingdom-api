package com.typenull.pingdom.analysis.api.dto;

import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record LocationAnalysisReportResponse(
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
        LocalDateTime createdAt,
        String downloadPath,
        String htmlDebugPath
) {
    public static LocationAnalysisReportResponse from(LocationAnalysisReport report) {
        String basePath = "/analysis/reports/" + report.getReportId();
        return new LocationAnalysisReportResponse(
                report.getReportId(), report.getReportName(), report.getCategory(), report.getRegion(),
                report.getTargetCustomerGroup(), report.getOperatingHours(), report.getEmail(),
                report.isPrivacyConsent(), report.getPublishedDate(), report.getAnalysisBasisDate(),
                report.getCreatedAt(), basePath + "/download", basePath + "/html"
        );
    }

    public static LocationAnalysisReportResponse fromSummary(
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
            LocalDateTime createdAt
    ) {
        String basePath = "/analysis/reports/" + reportId;
        return new LocationAnalysisReportResponse(
                reportId, reportName, category, region, targetCustomerGroup, operatingHours, email,
                privacyConsent, publishedDate, analysisBasisDate, createdAt,
                basePath + "/download", basePath + "/html"
        );
    }
}
