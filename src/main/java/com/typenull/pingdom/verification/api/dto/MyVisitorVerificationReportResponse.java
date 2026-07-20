package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.*;
import java.time.LocalDateTime;

public record MyVisitorVerificationReportResponse(
        Long id,
        Long placeId,
        VisitorVerificationReportType reportType,
        String description,
        String evidenceUrl,
        VisitorVerificationReportStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
    public static MyVisitorVerificationReportResponse from(VisitorVerificationReport report) {
        String rejectionReason = report.getStatus() == VisitorVerificationReportStatus.REJECTED
                ? report.getReviewNote()
                : null;
        return new MyVisitorVerificationReportResponse(report.getId(), report.getPlaceId(), report.getReportType(),
                report.getDescription(), report.getEvidenceUrl(), report.getStatus(), rejectionReason,
                report.getCreatedAt(), report.getReviewedAt(), report.getUpdatedAt());
    }
}
