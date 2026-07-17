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
@Table(name = "tourist_offer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TouristOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "benefit_description", nullable = false, length = 500)
    private String benefitDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    @Column(name = "coupon_validity_days", nullable = false)
    private int couponValidityDays;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private TouristOffer(
            Long merchantOwnerUserId,
            Long placeId,
            String title,
            String description,
            String benefitDescription,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int totalQuantity,
            int couponValidityDays,
            LocalDateTime now
    ) {
        this.merchantOwnerUserId = Objects.requireNonNull(merchantOwnerUserId);
        this.placeId = Objects.requireNonNull(placeId);
        this.title = requireText(title, 100);
        this.description = requireText(description, 1000);
        this.benefitDescription = requireText(benefitDescription, 500);
        validatePeriod(startsAt, endsAt);
        validateQuantity(totalQuantity, couponValidityDays);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.totalQuantity = totalQuantity;
        this.couponValidityDays = couponValidityDays;
        this.issuedQuantity = 0;
        this.status = OfferStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static TouristOffer draft(
            Long merchantOwnerUserId,
            Long placeId,
            String title,
            String description,
            String benefitDescription,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int totalQuantity,
            int couponValidityDays,
            LocalDateTime now
    ) {
        return new TouristOffer(
                merchantOwnerUserId,
                placeId,
                title,
                description,
                benefitDescription,
                startsAt,
                endsAt,
                totalQuantity,
                couponValidityDays,
                now
        );
    }

    public void publish(LocalDateTime now) {
        if (status != OfferStatus.DRAFT || !endsAt.isAfter(now)) {
            throw new IllegalStateException("게시할 수 없는 Offer 상태입니다.");
        }
        status = OfferStatus.PUBLISHED;
        updatedAt = now;
    }

    public void close(LocalDateTime now) {
        if (status == OfferStatus.CLOSED) {
            return;
        }
        if (status != OfferStatus.PUBLISHED) {
            throw new IllegalStateException("종료할 수 없는 Offer 상태입니다.");
        }
        status = OfferStatus.CLOSED;
        updatedAt = now;
    }

    public LocalDateTime issueCoupon(LocalDateTime now) {
        if (!isAvailableAt(now)) {
            throw new IllegalStateException("현재 발급할 수 없는 Offer입니다.");
        }
        if (issuedQuantity >= totalQuantity) {
            throw new IllegalArgumentException("Offer의 쿠폰이 모두 발급되었습니다.");
        }
        issuedQuantity++;
        updatedAt = now;
        LocalDateTime validityEnd = now.plusDays(couponValidityDays);
        return validityEnd.isBefore(endsAt) ? validityEnd : endsAt;
    }

    public boolean isAvailableAt(LocalDateTime now) {
        return status == OfferStatus.PUBLISHED
                && !now.isBefore(startsAt)
                && now.isBefore(endsAt);
    }

    public boolean isSoldOut() {
        return issuedQuantity >= totalQuantity;
    }

    private static void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Offer 종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    private static void validateQuantity(int totalQuantity, int couponValidityDays) {
        if (totalQuantity <= 0 || couponValidityDays < 1 || couponValidityDays > 365) {
            throw new IllegalArgumentException("Offer 수량 또는 쿠폰 유효 기간이 올바르지 않습니다.");
        }
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException("Offer 문자열 값은 비어 있을 수 없습니다.");
        }
        return value.trim();
    }
}
