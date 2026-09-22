package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import java.time.LocalDateTime;

/** 작성자용 현장 제보 응답으로 거절 사유만 조건부 공개하며 심사자 ID는 응답에서 제외. */
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

    /** REJECTED일 때만 reviewNote를 rejectionReason으로 노출하며 승인 메모는 반환 대상에서 제외. */
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
