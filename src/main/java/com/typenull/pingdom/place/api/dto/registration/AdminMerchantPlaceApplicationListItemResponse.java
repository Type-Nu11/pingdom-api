package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import java.time.LocalDateTime;

/** 사업자등록번호를 마스킹한 관리자 통합 신청 목록 항목입니다. */
public record AdminMerchantPlaceApplicationListItemResponse(
        Long id,
        Long applicantUserId,
        MerchantPlaceApplicationType applicationType,
        PlaceRegistrationStatus status,
        String legalName,
        String businessName,
        String maskedBusinessRegistrationNumber,
        String merchantDisplayName,
        String placeName,
        Long existingPlaceId,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt
) {
    public static AdminMerchantPlaceApplicationListItemResponse from(
            PlaceRegistrationApplication application,
            String businessRegistrationNumber
    ) {
        return new AdminMerchantPlaceApplicationListItemResponse(
                application.getId(),
                application.getApplicantUserId(),
                application.getApplicationType(),
                application.getStatus(),
                application.getLegalName(),
                application.getBusinessName(),
                mask(businessRegistrationNumber),
                application.getMerchantDisplayName(),
                application.getPlaceName(),
                application.getExistingPlaceId(),
                application.getSubmittedAt(),
                application.getUpdatedAt()
        );
    }

    private static String mask(String businessRegistrationNumber) {
        if (businessRegistrationNumber == null || businessRegistrationNumber.length() < 5) {
            return "***";
        }
        return businessRegistrationNumber.substring(0, 3) + "-****-"
                + businessRegistrationNumber.substring(businessRegistrationNumber.length() - 2);
    }
}
