package com.typenull.pingdom.place.api.dto.place.information.report;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportDispute;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "장소 정보 반박 응답")
public record PlaceInformationDisputeResponse(
        Long disputeId,
        Long reportId,
        Long disputedByUserId,
        String description,
        @Schema(nullable = true)
        String evidenceUrl,
        PlaceInformationDisputeStatus status,
        @Schema(nullable = true)
        Long reviewedByAdminUserId,
        @Schema(nullable = true)
        String reviewReason,
        @Schema(nullable = true)
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PlaceInformationDisputeResponse from(PlaceInformationReportDispute dispute) {
        return new PlaceInformationDisputeResponse(
                dispute.getId(),
                dispute.getReport().getId(),
                dispute.getDisputedByUserId(),
                dispute.getDescription(),
                dispute.getEvidenceUrl(),
                dispute.getStatus(),
                dispute.getReviewedByAdminUserId(),
                dispute.getReviewReason(),
                dispute.getReviewedAt(),
                dispute.getCreatedAt(),
                dispute.getUpdatedAt()
        );
    }
}
