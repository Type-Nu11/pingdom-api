package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내 방문자 검증 제보 정정 응답")
public record MyVisitorVerificationReportCorrectionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long reportId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisitorVerificationReportType reportType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String evidenceUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Integer waitTimeMinutes,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String languageCode,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) CouponUsageStatus couponUsageStatus,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) CrowdLevel crowdLevel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisitorVerificationReportStatus reportStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisitorVerificationReportCorrectionStatus status,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String rejectionReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime reviewedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedAt
) {
    public static MyVisitorVerificationReportCorrectionResponse from(
            VisitorVerificationReportCorrection correction
    ) {
        String rejectionReason = correction.getStatus() == VisitorVerificationReportCorrectionStatus.REJECTED
                ? correction.getReviewNote()
                : null;
        return new MyVisitorVerificationReportCorrectionResponse(
                correction.getId(),
                correction.getReport().getId(),
                correction.getReport().getPlaceId(),
                correction.getReportType(),
                correction.getDescription(),
                correction.getEvidenceUrl(),
                correction.getWaitTimeMinutes(),
                correction.getLanguageCode(),
                correction.getCouponUsageStatus(),
                correction.getCrowdLevel(),
                correction.getReport().getStatus(),
                correction.getStatus(),
                rejectionReason,
                correction.getCreatedAt(),
                correction.getReviewedAt(),
                correction.getUpdatedAt()
        );
    }
}
