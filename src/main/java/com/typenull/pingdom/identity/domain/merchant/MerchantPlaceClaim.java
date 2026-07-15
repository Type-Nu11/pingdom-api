package com.typenull.pingdom.identity.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "merchant_place_claim")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MerchantPlaceClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "claim_reason", nullable = false, length = 500)
    private String claimReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MerchantPlaceClaimStatus status;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static MerchantPlaceClaim pending(
            Long merchantOwnerUserId,
            Long placeId,
            String claimReason,
            LocalDateTime now
    ) {
        return MerchantPlaceClaim.builder()
                .merchantOwnerUserId(merchantOwnerUserId)
                .placeId(placeId)
                .claimReason(claimReason)
                .status(MerchantPlaceClaimStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void approve(Long adminUserId, String reason, LocalDateTime now) {
        requirePending();
        status = MerchantPlaceClaimStatus.APPROVED;
        reviewReason = reason;
        reviewedBy = adminUserId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void reject(Long adminUserId, String reason, LocalDateTime now) {
        requirePending();
        status = MerchantPlaceClaimStatus.REJECTED;
        reviewReason = reason;
        reviewedBy = adminUserId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void cancel(LocalDateTime now) {
        requirePending();
        status = MerchantPlaceClaimStatus.CANCELED;
        updatedAt = now;
    }

    public boolean isPending() {
        return status == MerchantPlaceClaimStatus.PENDING;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("심사 대기 중인 장소 Claim만 처리할 수 있습니다.");
        }
    }
}
