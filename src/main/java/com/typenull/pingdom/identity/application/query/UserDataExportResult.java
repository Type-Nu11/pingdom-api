package com.typenull.pingdom.identity.application.query;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserDataExportResult(
        ExportUser user,
        List<ExportBookmark> bookmarks,
        List<Long> likedMapImageIds,
        List<ExportTravelSchedule> travelSchedules,
        ExportCurrentActivityIntent currentActivityIntent,
        ExportMerchantOwnerProfile merchantOwnerProfile,
        ExportMerchantVerification merchantVerification
) {

    public static UserDataExportResult of(
            User user,
            List<ExportBookmark> bookmarks,
            List<Long> likedMapImageIds,
            List<TravelSchedule> travelSchedules,
            UserCurrentActivityIntent currentActivityIntent,
            MerchantOwnerProfile merchantOwnerProfile,
            List<Long> merchantOwnerPlaceIds,
            MerchantVerification merchantVerification,
            String businessRegistrationNumber
    ) {
        return new UserDataExportResult(
                new ExportUser(user.getId(), user.getUsername(), user.getProfileImageUrl()),
                bookmarks,
                likedMapImageIds,
                travelSchedules.stream()
                        .map(schedule -> new ExportTravelSchedule(
                                schedule.getId(),
                                schedule.getStartDate(),
                                schedule.getEndDate(),
                                schedule.getState()
                        ))
                        .toList(),
                currentActivityIntent == null
                        ? null
                        : new ExportCurrentActivityIntent(
                                currentActivityIntent.getIntent(),
                                currentActivityIntent.getExpiresAt()
                        ),
                merchantOwnerProfile == null
                        ? null
                        : new ExportMerchantOwnerProfile(
                                merchantOwnerProfile.getBusinessName(),
                                merchantOwnerProfile.getDisplayName(),
                                merchantOwnerProfile.getDescription(),
                                merchantOwnerProfile.getContactEmail(),
                                merchantOwnerProfile.getContactPhone(),
                                merchantOwnerProfile.getStatus(),
                                merchantOwnerPlaceIds
                        ),
                merchantVerification == null
                        ? null
                        : new ExportMerchantVerification(
                                merchantVerification.getLegalName(),
                                merchantVerification.getBusinessName(),
                                businessRegistrationNumber,
                                merchantVerification.getIdentityStatus(),
                                merchantVerification.getBusinessStatus(),
                                merchantVerification.getReviewReason(),
                                merchantVerification.getReviewedAt()
                        )
        );
    }

    public record ExportUser(
            Long id,
            String username,
            String profileImageUrl
    ) {
    }

    public record ExportBookmark(
            Long id,
            Long placeId
    ) {
    }

    public record ExportTravelSchedule(
            Long id,
            LocalDate startDate,
            LocalDate endDate,
            TravelScheduleState state
    ) {
    }

    public record ExportCurrentActivityIntent(
            CurrentActivityIntent intent,
            LocalDateTime expiresAt
    ) {
    }

    public record ExportMerchantOwnerProfile(
            String businessName,
            String displayName,
            String description,
            String contactEmail,
            String contactPhone,
            MerchantOwnerStatus status,
            List<Long> placeIds
    ) {
    }

    public record ExportMerchantVerification(
            String legalName,
            String businessName,
            String businessRegistrationNumber,
            MerchantVerificationStatus identityStatus,
            MerchantVerificationStatus businessStatus,
            String reviewReason,
            LocalDateTime reviewedAt
    ) {
    }
}
