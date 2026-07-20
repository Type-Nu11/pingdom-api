package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.*;
import java.time.LocalDateTime;

public record VisitorVerificationReportResponse(
        Long id,
        Long reporterUserId,
        Long placeId,
        VisitorVerificationReportType reportType,
        String description,
        String evidenceUrl,
        VisitorVerificationReportStatus status,
        Long reviewerAdminUserId,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
    public static VisitorVerificationReportResponse from(VisitorVerificationReport report) {
        return new VisitorVerificationReportResponse(report.getId(), report.getReporterUserId(), report.getPlaceId(),
                report.getReportType(), report.getDescription(), report.getEvidenceUrl(), report.getStatus(),
                report.getReviewerAdminUserId(), report.getReviewNote(), report.getCreatedAt(), report.getReviewedAt(),
                report.getUpdatedAt());
    }
}
