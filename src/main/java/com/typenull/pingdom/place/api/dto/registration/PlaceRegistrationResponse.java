package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationCategory;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationTag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record PlaceRegistrationResponse(
        Long id, Long applicantUserId, PlaceRegistrationStatus status, String placeName,
        PlaceRegistrationCategory category, double latitude, double longitude, String roadAddress,
        String jibunAddress, String postalCode, String description, String businessContactPhone, String reviewReason,
        Long registeredPlaceId, LocalDateTime submittedAt, LocalDateTime reviewedAt,
        LocalDateTime registeredAt, LocalDateTime createdAt, LocalDateTime updatedAt,
        long submissionVersion, String submissionContentHash, LocalDateTime canceledAt,
        Set<PlaceRegistrationTag> tags, List<PlaceRegistrationAttachmentResponse> attachments
) {
    public static PlaceRegistrationResponse from(PlaceRegistrationApplication a) {
        return new PlaceRegistrationResponse(a.getId(), a.getApplicantUserId(), a.getStatus(), a.getPlaceName(),
                a.getCategory(), a.getLatitude(), a.getLongitude(), a.getRoadAddress(), a.getJibunAddress(),
                a.getPostalCode(), a.getDescription(), a.getBusinessContactPhone(), a.getReviewReason(), a.getRegisteredPlaceId(),
                a.getSubmittedAt(), a.getReviewedAt(), a.getRegisteredAt(), a.getCreatedAt(), a.getUpdatedAt(),
                a.getSubmissionVersion(), a.getSubmissionContentHash(), a.getCanceledAt(), a.getTags(),
                a.getAttachments().stream().map(PlaceRegistrationAttachmentResponse::from).toList());
    }
}
