package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import java.time.LocalDateTime;

/** 관리자 심사용 제보 응답으로 작성자·심사자 ID와 심사 메모를 함께 제공한다. */
public record ScoutFieldReportResponse(
        Long id,
        Long scoutUserId,
        Long placeId,
        ScoutFieldReportType reportType,
        String description,
        String evidenceUrl,
        ScoutFieldReportStatus status,
        Long reviewerAdminUserId,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {

    public static ScoutFieldReportResponse from(ScoutFieldReport report) {
        return new ScoutFieldReportResponse(
                report.getId(),
                report.getScoutUserId(),
                report.getPlaceId(),
                report.getReportType(),
                report.getDescription(),
                report.getEvidenceUrl(),
                report.getStatus(),
                report.getReviewerAdminUserId(),
                report.getReviewNote(),
                report.getCreatedAt(),
                report.getReviewedAt(),
                report.getUpdatedAt()
        );
    }
}
