package com.typenull.pingdom.moderation.api.dto.place.quality.information;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidenceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 장소 정보 증빙 항목")
public record AdminPlaceInformationEvidenceItem(
        Long evidenceId,
        Long placeId,
        PlaceInformationSourceType sourceType,
        PlaceInformationEvidenceType evidenceType,
        PlaceInformationVerificationStatus verificationStatus,
        @Schema(nullable = true)
        String externalReference,
        @Schema(nullable = true)
        String referenceUrl,
        @Schema(nullable = true)
        String description,
        @Schema(nullable = true)
        Long submittedByUserId,
        @Schema(nullable = true)
        Long reviewedByAdminUserId,
        @Schema(nullable = true)
        String reviewReason,
        LocalDateTime submittedAt,
        @Schema(nullable = true)
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminPlaceInformationEvidenceItem from(PlaceInformationEvidence evidence) {
        return new AdminPlaceInformationEvidenceItem(
                evidence.getId(),
                evidence.getPlace().getId(),
                evidence.getSourceType(),
                evidence.getEvidenceType(),
                evidence.getVerificationStatus(),
                evidence.getExternalReference(),
                evidence.getReferenceUrl(),
                evidence.getDescription(),
                evidence.getSubmittedByUserId(),
                evidence.getReviewedByAdminUserId(),
                evidence.getReviewReason(),
                evidence.getSubmittedAt(),
                evidence.getReviewedAt(),
                evidence.getCreatedAt(),
                evidence.getUpdatedAt()
        );
    }
}
