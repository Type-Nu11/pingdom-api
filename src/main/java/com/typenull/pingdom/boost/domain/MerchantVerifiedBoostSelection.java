package com.typenull.pingdom.boost.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "merchant_verified_boost_selection")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MerchantVerifiedBoostSelection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;

    public static MerchantVerifiedBoostSelection create(Long productId, Long ownerId, Long placeId,
            String idempotencyKey, LocalDateTime now) {
        MerchantVerifiedBoostSelection selection = new MerchantVerifiedBoostSelection();
        selection.productId = Objects.requireNonNull(productId);
        selection.merchantOwnerUserId = Objects.requireNonNull(ownerId);
        selection.placeId = Objects.requireNonNull(placeId);
        selection.idempotencyKey = requireKey(idempotencyKey);
        selection.selectedAt = Objects.requireNonNull(now);
        return selection;
    }

    private static String requireKey(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 64) {
            throw new IllegalArgumentException("idempotencyKey가 올바르지 않습니다.");
        }
        return value.trim();
    }
}
