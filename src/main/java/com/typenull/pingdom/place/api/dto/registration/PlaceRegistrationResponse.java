package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import java.time.LocalDateTime;

public record PlaceRegistrationResponse(
        Long id, Long applicantUserId, PlaceRegistrationStatus status, String placeName,
        PlaceRegistrationCategory category, double latitude, double longitude, String roadAddress,
        String jibunAddress, String postalCode, String description, String reviewReason,
        Long registeredPlaceId, LocalDateTime submittedAt, LocalDateTime reviewedAt,
        LocalDateTime registeredAt, LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static PlaceRegistrationResponse from(PlaceRegistrationApplication a) {
        return new PlaceRegistrationResponse(a.getId(), a.getApplicantUserId(), a.getStatus(), a.getPlaceName(),
                a.getCategory(), a.getLatitude(), a.getLongitude(), a.getRoadAddress(), a.getJibunAddress(),
                a.getPostalCode(), a.getDescription(), a.getReviewReason(), a.getRegisteredPlaceId(),
                a.getSubmittedAt(), a.getReviewedAt(), a.getRegisteredAt(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
