package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import java.time.LocalDateTime;

public record VisitorVerificationReportCorrectionResponse(
        Long id,
        Long reportId,
        Long requesterUserId,
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
        Long reviewerAdminUserId,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
    public static VisitorVerificationReportCorrectionResponse from(
            VisitorVerificationReportCorrection correction
    ) {
        return new VisitorVerificationReportCorrectionResponse(
                correction.getId(),
                correction.getReport().getId(),
                correction.getRequesterUserId(),
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
                correction.getReviewerAdminUserId(),
                correction.getReviewNote(),
                correction.getCreatedAt(),
                correction.getReviewedAt(),
                correction.getUpdatedAt()
        );
    }
}
