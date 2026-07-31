package com.typenull.pingdom.offer.infrastructure;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import java.time.LocalDateTime;

final class PlaceMerchantOfferFixture {

    private PlaceMerchantOfferFixture() {
    }

    static User user(String suffix, UserRole role) {
        return user(suffix, role, UserStatus.ACTIVE, false);
    }

    static User user(String suffix, UserRole role, UserStatus status, boolean banned) {
        return User.builder()
                .username("data-fixture-" + suffix)
                .email("data-fixture-" + suffix + "@example.com")
                .emailVerified(true)
                .password("encoded-password")
                .birthYear(1990)
                .language("ko")
                .country("KR")
                .role(role)
                .status(status)
                .withdrawnAt(status == UserStatus.WITHDRAWN ? LocalDateTime.of(2026, 7, 25, 12, 0) : null)
                .banned(banned)
                .bannedAt(banned ? LocalDateTime.of(2026, 7, 25, 12, 0) : null)
                .banReason(banned ? "통합 테스트 영구 밴" : null)
                .banType(banned ? UserBanType.PERMANENT : null)
                .build();
    }

    static MapPlace place(Long registrantId, String suffix) {
        return MapPlace.builder()
                .name("통합 테스트 장소 " + suffix)
                .address("서울특별시 중구 " + suffix)
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(registrantId)
                .registrant("fixture-merchant")
                .build();
    }

    static MerchantOwnerProfile profile(Long merchantId, MerchantOwnerStatus status, LocalDateTime now) {
        return MerchantOwnerProfile.builder()
                .userId(merchantId)
                .businessName("통합 테스트 상점")
                .displayName("Fixture Merchant")
                .description("Place, Merchant, Offer 데이터 계층 fixture")
                .contactEmail("merchant-" + merchantId + "@example.com")
                .contactPhone("010-0000-" + String.format("%04d", merchantId % 10_000))
                .status(status)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build();
    }

    static MerchantVerification verification(
            Long merchantId,
            MerchantVerificationStatus status,
            LocalDateTime now
    ) {
        return MerchantVerification.builder()
                .userId(merchantId)
                .legalName("Fixture Owner")
                .businessName("통합 테스트 상점")
                .encryptedBusinessRegistrationNumber("encrypted-" + merchantId)
                .identityStatus(status)
                .businessStatus(status)
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusDays(1))
                .build();
    }

    static MerchantOwnerPlace ownership(Long merchantId, Long placeId, LocalDateTime now) {
        return MerchantOwnerPlace.builder()
                .merchantOwnerUserId(merchantId)
                .placeId(placeId)
                .createdAt(now.minusDays(1))
                .build();
    }

    static TouristOffer publishedOffer(
            Long merchantId,
            Long placeId,
            String suffix,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int quantity,
            LocalDateTime publishedAt
    ) {
        TouristOffer offer = TouristOffer.draft(
                merchantId,
                placeId,
                "관광객 Offer " + suffix,
                "통합 테스트용 Offer " + suffix,
                "혜택 " + suffix,
                startsAt,
                endsAt,
                quantity,
                3,
                startsAt.minusHours(1)
        );
        offer.publish(publishedAt);
        return offer;
    }

    static TouristCoupon coupon(
            Long offerId,
            Long touristId,
            String code,
            LocalDateTime now
    ) {
        return TouristCoupon.issue(offerId, touristId, code, now, now.plusDays(1));
    }
}
