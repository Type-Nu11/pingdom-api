package com.typenull.pingdom.boost.domain;

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
@Table(name = "verified_boost_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerifiedBoostProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerifiedBoostProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static VerifiedBoostProduct draft(String name, String description,
            long priceAmount, int durationDays, LocalDateTime now) {
        if (priceAmount <= 0 || durationDays < 1 || durationDays > 365) {
            throw new IllegalArgumentException("Boost 가격 또는 적용 기간이 올바르지 않습니다.");
        }
        VerifiedBoostProduct product = new VerifiedBoostProduct();
        product.name = requireText(name, 100);
        product.description = requireText(description, 500);
        product.priceAmount = priceAmount;
        product.durationDays = durationDays;
        product.status = VerifiedBoostProductStatus.DRAFT;
        product.createdAt = Objects.requireNonNull(now, "now must not be null");
        product.updatedAt = now;
        return product;
    }

    public void activate(LocalDateTime now) {
        if (status == VerifiedBoostProductStatus.ACTIVE) {
            return;
        }
        if (status != VerifiedBoostProductStatus.DRAFT && status != VerifiedBoostProductStatus.INACTIVE) {
            throw new IllegalStateException("활성화할 수 없는 Boost 상품 상태입니다.");
        }
        status = VerifiedBoostProductStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void deactivate(LocalDateTime now) {
        if (status == VerifiedBoostProductStatus.INACTIVE) {
            return;
        }
        if (status != VerifiedBoostProductStatus.ACTIVE) {
            throw new IllegalStateException("비활성화할 수 없는 Boost 상품 상태입니다.");
        }
        status = VerifiedBoostProductStatus.INACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException("Boost 상품 문자열 값이 올바르지 않습니다.");
        }
        return value.trim();
    }
}
