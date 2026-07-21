package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import java.time.LocalDateTime;

public record MyScoutFieldReportResponse(
        Long id,
        Long placeId,
        ScoutFieldReportType reportType,
        String description,
        String evidenceUrl,
        ScoutFieldReportStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {

    public static MyScoutFieldReportResponse from(ScoutFieldReport report) {
        String rejectionReason = report.getStatus() == ScoutFieldReportStatus.REJECTED
                ? report.getReviewNote()
                : null;
        return new MyScoutFieldReportResponse(
                report.getId(),
                report.getPlaceId(),
                report.getReportType(),
                report.getDescription(),
                report.getEvidenceUrl(),
                report.getStatus(),
                rejectionReason,
                report.getCreatedAt(),
                report.getReviewedAt(),
                report.getUpdatedAt()
        );
    }
}
