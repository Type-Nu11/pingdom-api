package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import java.time.LocalDateTime;

public record MyVisitorVerificationReportCorrectionResponse(
        Long id,
        Long reportId,
        Long placeId,
        VisitorVerificationReportType reportType,
        String description,
        String evidenceUrl,
        Integer waitTimeMinutes,
        String languageCode,
        CouponUsageStatus couponUsageStatus,
        CrowdLevel crowdLevel,
        VisitorVerificationReportStatus reportStatus,
        VisitorVerificationReportCorrectionStatus status,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
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
