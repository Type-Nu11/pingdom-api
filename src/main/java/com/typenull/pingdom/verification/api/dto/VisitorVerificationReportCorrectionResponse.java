package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportStatus;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import java.time.LocalDateTime;

/** 관리자 정정 심사용 응답이다. 원본 제보의 현재 상태와 정정 요청의 상태를 별도로 반환한다. */
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
    /** 정정 요청의 내용과 심사 이력에 원본 제보의 식별자·장소·현재 상태를 결합한다. */
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
