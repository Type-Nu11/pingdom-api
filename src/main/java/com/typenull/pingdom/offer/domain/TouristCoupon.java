package com.typenull.pingdom.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "tourist_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** 발급된 관광 쿠폰의 유효 기간, 사용 주체와 사용 상태를 관리합니다. */
public class TouristCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    /** 발급 이후 Offer·장소 변경과 무관하게 쿠폰 표기를 유지하는 발급 시점 스냅샷입니다. */
    @Column(name = "offer_title", length = 100)
    private String offerTitle;

    @Column(name = "benefit_description", length = 500)
    private String benefitDescription;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "place_name", length = 100)
    private String placeName;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 36)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "redeemed_at")
    private LocalDateTime redeemedAt;

    @Column(name = "redeemed_by")
    private Long redeemedBy;

    @Version
    @Column(nullable = false)
    private long version;

    private TouristCoupon(
            Long offerId,
            String offerTitle,
            String benefitDescription,
            Long placeId,
            String placeName,
            Long userId,
            String code,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        this.offerId = Objects.requireNonNull(offerId);
        this.offerTitle = offerTitle;
        this.benefitDescription = benefitDescription;
        this.placeId = placeId;
        this.placeName = placeName;
        this.userId = Objects.requireNonNull(userId);
        this.code = Objects.requireNonNull(code);
        this.issuedAt = Objects.requireNonNull(issuedAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("쿠폰 만료 시각은 발급 시각보다 늦어야 합니다.");
        }
        this.status = CouponStatus.ISSUED;
    }

    public static TouristCoupon issue(
            Long offerId,
            Long userId,
            String code,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        return new TouristCoupon(offerId, null, null, null, null, userId, code, issuedAt, expiresAt);
    }

    public static TouristCoupon issue(
            Long offerId,
            String offerTitle,
            String benefitDescription,
            Long placeId,
            String placeName,
            Long userId,
            String code,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
        return new TouristCoupon(
                offerId,
                offerTitle,
                benefitDescription,
                placeId,
                placeName,
                userId,
                code,
                issuedAt,
                expiresAt
        );
    }

    /** 유효 기간과 사용 권한을 확인한 뒤 쿠폰을 사용 완료 상태로 전환합니다. */
    public void redeem(Long merchantOwnerUserId, LocalDateTime now) {
        Objects.requireNonNull(merchantOwnerUserId);
        if (status != CouponStatus.ISSUED || !now.isBefore(expiresAt)) {
            throw new IllegalStateException("사용할 수 없는 쿠폰입니다.");
        }
        status = CouponStatus.REDEEMED;
        redeemedBy = merchantOwnerUserId;
        redeemedAt = now;
    }

    public CouponStatus statusAt(LocalDateTime now) {
        if (status == CouponStatus.ISSUED && !now.isBefore(expiresAt)) {
            return CouponStatus.EXPIRED;
        }
        return status;
    }
}
