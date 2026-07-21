package com.typenull.pingdom.place.api.dto.place.information.report;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReport;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "장소 정보 신고 응답")
public record PlaceInformationReportResponse(
        Long reportId,
        Long placeId,
        @Schema(nullable = true)
        Long evidenceId,
        Long reporterUserId,
        PlaceInformationReportTargetType targetType,
        PlaceInformationReportReasonType reasonType,
        String description,
        @Schema(nullable = true)
        String evidenceUrl,
        PlaceInformationReportStatus status,
        @Schema(nullable = true)
        Long reviewedByAdminUserId,
        @Schema(nullable = true)
        String reviewReason,
        @Schema(nullable = true)
        LocalDateTime reviewedAt,
        @Schema(nullable = true)
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PlaceInformationDisputeResponse> disputes
) {
    public static PlaceInformationReportResponse from(PlaceInformationReport report) {
        return new PlaceInformationReportResponse(
                report.getId(),
                report.getPlace().getId(),
                report.getEvidence() == null ? null : report.getEvidence().getId(),
                report.getReporterUserId(),
                report.getTargetType(),
                report.getReasonType(),
                report.getDescription(),
                report.getEvidenceUrl(),
                report.getStatus(),
                report.getReviewedByAdminUserId(),
                report.getReviewReason(),
                report.getReviewedAt(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                report.currentDisputes().stream().map(PlaceInformationDisputeResponse::from).toList()
        );
    }
}
