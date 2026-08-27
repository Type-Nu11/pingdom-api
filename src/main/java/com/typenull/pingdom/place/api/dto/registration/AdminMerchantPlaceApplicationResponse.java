package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import java.time.LocalDateTime;
import java.util.List;

/** MERCHANT_REVIEW 권한과 감사 로그를 거친 관리자 통합 신청 상세 응답입니다. */
public record AdminMerchantPlaceApplicationResponse(
        Long id,
        Long applicantUserId,
        MerchantPlaceApplicationType applicationType,
        PlaceRegistrationStatus status,
        String legalName,
        String businessName,
        String businessRegistrationNumber,
        String merchantDisplayName,
        String merchantContactEmail,
        String merchantDescription,
        String merchantContactPhone,
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
        List<AdminMerchantPlaceApplicationAttachmentResponse> attachments
) {
    public static AdminMerchantPlaceApplicationResponse from(
            PlaceRegistrationApplication application,
            String businessRegistrationNumber,
            List<AdminMerchantPlaceApplicationAttachmentResponse> attachments
    ) {
        return new AdminMerchantPlaceApplicationResponse(
                application.getId(), application.getApplicantUserId(), application.getApplicationType(), application.getStatus(),
                application.getLegalName(), application.getBusinessName(), businessRegistrationNumber,
                application.getMerchantDisplayName(), application.getMerchantContactEmail(), application.getMerchantDescription(),
                application.getMerchantContactPhone(), application.getPlaceName(), application.getExistingPlaceId(),
                application.getClaimReason(), application.getReviewReason(), application.getCompletedPlaceId(), application.getSubmittedAt(),
                application.getReviewedAt(), application.getCompletedAt(), application.getCanceledAt(), application.getCreatedAt(),
                application.getUpdatedAt(), application.getVersion(), application.getSubmissionVersion(), attachments
        );
    }
}
