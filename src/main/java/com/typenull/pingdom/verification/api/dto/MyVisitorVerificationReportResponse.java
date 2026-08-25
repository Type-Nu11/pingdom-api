package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 방문자 검증 제보 응답")
public record MyVisitorVerificationReportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisitorVerificationReportType reportType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String evidenceUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Integer waitTimeMinutes,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String languageCode,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) CouponUsageStatus couponUsageStatus,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) CrowdLevel crowdLevel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisitorVerificationReportStatus status,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String rejectionReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime reviewedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedAt
) {
    public static MyVisitorVerificationReportResponse from(VisitorVerificationReport report) {
        String rejectionReason = report.getStatus() == VisitorVerificationReportStatus.REJECTED
                ? report.getReviewNote()
                : null;
        return new MyVisitorVerificationReportResponse(report.getId(), report.getPlaceId(), report.getReportType(),
                report.getDescription(), report.getEvidenceUrl(), report.getWaitTimeMinutes(), report.getLanguageCode(),
                report.getCouponUsageStatus(), report.getCrowdLevel(), report.getStatus(), rejectionReason,
                report.getCreatedAt(), report.getReviewedAt(), report.getUpdatedAt());
    }
}
