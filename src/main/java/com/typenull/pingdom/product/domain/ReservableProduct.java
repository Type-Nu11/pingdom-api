package com.typenull.pingdom.product.domain;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "reservable_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservableProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private AvailabilityProductType productType;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservableProductStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version @Column(nullable = false)
    private long version;

    public static ReservableProduct create(Long ownerId, Long placeId, AvailabilityProductType productType,
            String name, LocalDateTime now) {
        if (productType == null || productType == AvailabilityProductType.GENERAL) {
            throw new IllegalArgumentException("티켓 또는 클래스 상품 유형이 필요합니다.");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("상품명이 필요합니다.");
        ReservableProduct product = new ReservableProduct();
        product.merchantOwnerUserId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        product.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        product.productType = productType;
        product.name = name.trim();
        product.status = ReservableProductStatus.ACTIVE;
        product.createdAt = Objects.requireNonNull(now, "now must not be null");
        product.updatedAt = now;
        return product;
    }

    public void changeStatus(boolean active, LocalDateTime now) {
        status = active ? ReservableProductStatus.ACTIVE : ReservableProductStatus.INACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }
}
