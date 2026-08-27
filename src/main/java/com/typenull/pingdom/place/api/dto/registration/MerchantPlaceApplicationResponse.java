package com.typenull.pingdom.place.api.dto.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 민감한 사업자등록번호를 제외한 통합 신청 조회 응답입니다. */
public record MerchantPlaceApplicationResponse(
        Long id,
        Long applicantUserId,
        MerchantPlaceApplicationType applicationType,
        PlaceRegistrationStatus status,
        String legalName,
        String businessName,
        String merchantDisplayName,
        String merchantContactEmail,
        String merchantDescription,
        String merchantContactPhone,
        MerchantPlaceApplicationNewPlaceResponse newPlace,
        String placeName,
        Long existingPlaceId,
        String claimReason,
        String reviewReason,
        Long placeId,
        LocalDateTime submittedAt,
        LocalDateTime reviewedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long version,
        long submissionVersion,
        List<MerchantPlaceApplicationAttachmentResponse> attachments
) {
    public static MerchantPlaceApplicationResponse from(PlaceRegistrationApplication application, ObjectMapper objectMapper) {
        return new MerchantPlaceApplicationResponse(
                application.getId(), application.getApplicantUserId(), application.getApplicationType(), application.getStatus(),
                application.getLegalName(), application.getBusinessName(), application.getMerchantDisplayName(),
                application.getMerchantContactEmail(), application.getMerchantDescription(), application.getMerchantContactPhone(),
                application.getApplicationType() == MerchantPlaceApplicationType.NEW_PLACE
                        ? MerchantPlaceApplicationNewPlaceResponse.from(application, objectMapper)
                        : null,
                application.getPlaceName(), application.getExistingPlaceId(), application.getClaimReason(), application.getReviewReason(), application.getCompletedPlaceId(),
                application.getSubmittedAt(), application.getReviewedAt(), application.getCompletedAt(), application.getCanceledAt(),
                application.getCreatedAt(), application.getUpdatedAt(), application.getVersion(), application.getSubmissionVersion(),
                application.getAttachments().stream().map(MerchantPlaceApplicationAttachmentResponse::from).toList()
        );
    }
}
