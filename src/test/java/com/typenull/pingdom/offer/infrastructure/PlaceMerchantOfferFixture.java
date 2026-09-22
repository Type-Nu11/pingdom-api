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

    /**
     * Offer 데이터 계층의 정적 fixture 생성 모음이므로 인스턴스화를 차단.
     */
    private PlaceMerchantOfferFixture() {
    }

    /**
     * 지정 역할의 활성·미차단 사용자를 만들어 정상 자격 조건의 기본값을 제공.
     */
    static User user(String suffix, UserRole role) {
        return user(suffix, role, UserStatus.ACTIVE, false);
    }

    /**
     * 역할·활성/탈퇴 상태·영구 밴 여부에 맞춰 사용자와 관련 시각·사유를 구성해 Offer 공개 제외 조건을 재현.
     */
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

    /**
     * 주어진 등록자 ID와 구분 문자열로 고정 좌표의 장소를 생성. 소유 관계 엔티티는 별도 생성 대상.
     */
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

    /**
     * 지정한 점주 프로필 상태와 연락처·이전 생성/수정 시각을 구성해 활성 여부의 조회 조건을 제공.
     */
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

    /**
     * 점주의 본인·사업자 검증을 같은 지정 상태로 설정해 승인/대기 자격 경계를 재현.
     */
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

    /**
     * 점주와 장소의 현재 소유 관계를 만들어 Offer 작성자와 실제 소유자 비교에 사용.
     */
    static MerchantOwnerPlace ownership(Long merchantId, Long placeId, LocalDateTime now) {
        return MerchantOwnerPlace.builder()
                .merchantOwnerUserId(merchantId)
                .placeId(placeId)
                .createdAt(now.minusDays(1))
                .build();
    }

    /**
     * 지정 점주·장소·기간·수량으로 초안을 만들고 주어진 시각에 게시해 공개 조회 테스트 입력을 제공.
     */
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

    /**
     * 주어진 Offer·관광객·코드와 발급 시각으로 하루 유효 쿠폰을 만들어 고유 제약 및 롤백을 검증.
     */
    static TouristCoupon coupon(
            Long offerId,
            Long touristId,
            String code,
            LocalDateTime now
    ) {
        return TouristCoupon.issue(offerId, touristId, code, now, now.plusDays(1));
    }
}
