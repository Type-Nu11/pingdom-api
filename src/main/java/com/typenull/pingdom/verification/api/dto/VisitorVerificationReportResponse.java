package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.*;
import java.time.LocalDateTime;

/** 관리자용 방문 제보 상세로 작성자 ID와 심사자·메모를 포함. 구조화 값은 제보 유형에 따라 null일 수 있음. */
public record VisitorVerificationReportResponse(
        Long id,
        Long reporterUserId,
        Long placeId,
        VisitorVerificationReportType reportType,
        String description,
        String evidenceUrl,
        Integer waitTimeMinutes,
        String languageCode,
        CouponUsageStatus couponUsageStatus,
        CrowdLevel crowdLevel,
        VisitorVerificationReportStatus status,
        Long reviewerAdminUserId,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
    public static VisitorVerificationReportResponse from(VisitorVerificationReport report) {
        return new VisitorVerificationReportResponse(report.getId(), report.getReporterUserId(), report.getPlaceId(),
                report.getReportType(), report.getDescription(), report.getEvidenceUrl(), report.getWaitTimeMinutes(),
                report.getLanguageCode(), report.getCouponUsageStatus(), report.getCrowdLevel(), report.getStatus(),
                report.getReviewerAdminUserId(), report.getReviewNote(), report.getCreatedAt(), report.getReviewedAt(),
                report.getUpdatedAt());
    }
}
