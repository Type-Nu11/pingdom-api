package com.typenull.pingdom.identity.api.dto.export;

import com.typenull.pingdom.identity.application.query.UserDataExportResult;
import com.typenull.pingdom.identity.domain.travel.CurrentActivityIntent;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleState;
import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.OfferStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "사용자 데이터 내보내기 응답")
public record UserDataExportResponse(
        @Schema(description = "사용자 기본 정보")
        ExportUserResponse user,
        @Schema(description = "사용자 북마크 전체 목록")
        List<ExportBookmarkResponse> bookmarks,
        @Schema(description = "최근 좋아요한 지도 이미지 ID 목록. 최대 50개")
        List<Long> likedMapImageIds,
        @Schema(description = "사용자 여행 일정 목록")
        List<ExportTravelScheduleResponse> travelSchedules,
        @Schema(description = "만료되지 않은 현재 행동 의도. 없으면 null", nullable = true)
        ExportCurrentActivityIntentResponse currentActivityIntent,
        @Schema(description = "Merchant Owner 신청 및 프로필. 없으면 null", nullable = true)
        ExportMerchantOwnerProfileResponse merchantOwnerProfile,
        @Schema(description = "상점 장소 Claim 요청 이력")
        List<ExportMerchantPlaceClaimResponse> merchantPlaceClaims,
        @Schema(description = "Merchant 신원 및 사업자 검증 신청. 없으면 null", nullable = true)
        ExportMerchantVerificationResponse merchantVerification,
        @Schema(description = "Merchant Owner가 만든 관광객 전용 Offer 이력")
        List<ExportTouristOfferResponse> touristOffers,
        @Schema(description = "사용자가 발급받은 관광객 Coupon 이력")
        List<ExportTouristCouponResponse> touristCoupons
) {

    public static UserDataExportResponse from(UserDataExportResult result) {
        return new UserDataExportResponse(
                ExportUserResponse.from(result.user()),
                result.bookmarks().stream()
                        .map(ExportBookmarkResponse::from)
                        .toList(),
                result.likedMapImageIds(),
                result.travelSchedules().stream()
                        .map(ExportTravelScheduleResponse::from)
                        .toList(),
                ExportCurrentActivityIntentResponse.from(result.currentActivityIntent()),
                ExportMerchantOwnerProfileResponse.from(result.merchantOwnerProfile()),
                result.merchantPlaceClaims().stream()
                        .map(ExportMerchantPlaceClaimResponse::from)
                        .toList(),
                ExportMerchantVerificationResponse.from(result.merchantVerification()),
                result.touristOffers().stream().map(ExportTouristOfferResponse::from).toList(),
                result.touristCoupons().stream().map(ExportTouristCouponResponse::from).toList()
        );
    }

    public record ExportUserResponse(
            @Schema(description = "사용자 ID", example = "1")
            Long id,
            @Schema(description = "사용자 아이디", example = "pingdom_user")
            String username,
            @Schema(description = "프로필 이미지 URL", example = "https://cdn.pingdom.com/profiles/user1.png", nullable = true)
            String profileImageUrl
    ) {

        private static ExportUserResponse from(UserDataExportResult.ExportUser user) {
            return new ExportUserResponse(user.id(), user.username(), user.profileImageUrl());
        }
    }

    public record ExportBookmarkResponse(
            @Schema(description = "북마크 ID", example = "10")
            Long id,
            @Schema(description = "북마크 대상 장소 ID", example = "123")
            Long placeId
    ) {

        private static ExportBookmarkResponse from(UserDataExportResult.ExportBookmark bookmark) {
            return new ExportBookmarkResponse(bookmark.id(), bookmark.placeId());
        }
    }

    public record ExportTravelScheduleResponse(
            @Schema(description = "여행 일정 ID", example = "1")
            Long id,
            @Schema(description = "여행 시작일", example = "2026-08-01")
            LocalDate startDate,
            @Schema(description = "여행 종료일", example = "2026-08-04")
            LocalDate endDate,
            @Schema(description = "저장된 일정 상태", example = "SCHEDULED")
            TravelScheduleState state
    ) {

        private static ExportTravelScheduleResponse from(UserDataExportResult.ExportTravelSchedule schedule) {
            return new ExportTravelScheduleResponse(
                    schedule.id(),
                    schedule.startDate(),
                    schedule.endDate(),
                    schedule.state()
            );
        }
    }

    public record ExportCurrentActivityIntentResponse(
            @Schema(description = "현재 행동 의도", example = "CAFE")
            CurrentActivityIntent intent,
            @Schema(description = "행동 의도 만료 시각")
            LocalDateTime expiresAt
    ) {

        private static ExportCurrentActivityIntentResponse from(
                UserDataExportResult.ExportCurrentActivityIntent currentActivityIntent
        ) {
            if (currentActivityIntent == null) {
                return null;
            }
            return new ExportCurrentActivityIntentResponse(
                    currentActivityIntent.intent(),
                    currentActivityIntent.expiresAt()
            );
        }
    }

    public record ExportMerchantOwnerProfileResponse(
            String businessName,
            String displayName,
            @Schema(nullable = true) String description,
            String contactEmail,
            String contactPhone,
            MerchantOwnerStatus status,
            List<Long> placeIds
    ) {
        private static ExportMerchantOwnerProfileResponse from(
                UserDataExportResult.ExportMerchantOwnerProfile profile
        ) {
            if (profile == null) {
                return null;
            }
            return new ExportMerchantOwnerProfileResponse(
                    profile.businessName(),
                    profile.displayName(),
                    profile.description(),
                    profile.contactEmail(),
                    profile.contactPhone(),
                    profile.status(),
                    profile.placeIds()
            );
        }
    }

    public record ExportMerchantVerificationResponse(
            String legalName,
            String businessName,
            String businessRegistrationNumber,
            MerchantVerificationStatus identityStatus,
            MerchantVerificationStatus businessStatus,
            @Schema(nullable = true) String reviewReason,
            @Schema(nullable = true) LocalDateTime reviewedAt
    ) {
        private static ExportMerchantVerificationResponse from(
                UserDataExportResult.ExportMerchantVerification verification
        ) {
            if (verification == null) {
                return null;
            }
            return new ExportMerchantVerificationResponse(
                    verification.legalName(),
                    verification.businessName(),
                    verification.businessRegistrationNumber(),
                    verification.identityStatus(),
                    verification.businessStatus(),
                    verification.reviewReason(),
                    verification.reviewedAt()
            );
        }
    }

    public record ExportMerchantPlaceClaimResponse(
            Long id,
            Long placeId,
            MerchantPlaceClaimStatus status,
            String claimReason,
            @Schema(nullable = true) String reviewReason,
            @Schema(nullable = true) LocalDateTime reviewedAt,
            LocalDateTime createdAt
    ) {
        private static ExportMerchantPlaceClaimResponse from(
                UserDataExportResult.ExportMerchantPlaceClaim claim
        ) {
            return new ExportMerchantPlaceClaimResponse(
                    claim.id(),
                    claim.placeId(),
                    claim.status(),
                    claim.claimReason(),
                    claim.reviewReason(),
                    claim.reviewedAt(),
                    claim.createdAt()
            );
        }
    }

    public record ExportTouristOfferResponse(
            Long id,
            Long placeId,
            String title,
            String description,
            String benefitDescription,
            OfferStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int totalQuantity,
            int issuedQuantity,
            int couponValidityDays,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        private static ExportTouristOfferResponse from(UserDataExportResult.ExportTouristOffer offer) {
            return new ExportTouristOfferResponse(
                    offer.id(),
                    offer.placeId(),
                    offer.title(),
                    offer.description(),
                    offer.benefitDescription(),
                    offer.status(),
                    offer.startsAt(),
                    offer.endsAt(),
                    offer.totalQuantity(),
                    offer.issuedQuantity(),
                    offer.couponValidityDays(),
                    offer.createdAt(),
                    offer.updatedAt()
            );
        }
    }

    public record ExportTouristCouponResponse(
            Long id,
            Long offerId,
            String code,
            CouponStatus status,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt,
            @Schema(nullable = true) LocalDateTime redeemedAt
    ) {
        private static ExportTouristCouponResponse from(UserDataExportResult.ExportTouristCoupon coupon) {
            return new ExportTouristCouponResponse(
                    coupon.id(),
                    coupon.offerId(),
                    coupon.code(),
                    coupon.status(),
                    coupon.issuedAt(),
                    coupon.expiresAt(),
                    coupon.redeemedAt()
            );
        }
    }
}
