package com.typenull.pingdom.identity.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "merchant_owner_place")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MerchantOwnerPlace {

    @Id
    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_quality_status", nullable = false, length = 20)
    @Builder.Default
    private MerchantOperationalQualityStatus operationalQualityStatus = MerchantOperationalQualityStatus.UNMEASURED;

    @Column(name = "reservation_response_rate", nullable = false)
    @Builder.Default
    private Integer reservationResponseRate = 0;

    @Column(name = "reservation_cancellation_rate", nullable = false)
    @Builder.Default
    private Integer reservationCancellationRate = 0;

    @Column(name = "no_show_rate", nullable = false)
    @Builder.Default
    private Integer noShowRate = 0;

    @Column(name = "quality_evaluated_at")
    private LocalDateTime qualityEvaluatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void transferOwnership(Long newOwnerUserId) {
        merchantOwnerUserId = newOwnerUserId;
    }

    public void updateOperationalQuality(
            MerchantOperationalQualityStatus status,
            int reservationResponseRate,
            int reservationCancellationRate,
            int noShowRate,
            LocalDateTime evaluatedAt
    ) {
        validateRate(reservationResponseRate);
        validateRate(reservationCancellationRate);
        validateRate(noShowRate);
        operationalQualityStatus = status;
        this.reservationResponseRate = reservationResponseRate;
        this.reservationCancellationRate = reservationCancellationRate;
        this.noShowRate = noShowRate;
        qualityEvaluatedAt = evaluatedAt;
    }

    private void validateRate(int rate) {
        if (rate < 0 || rate > 100) {
            throw new IllegalArgumentException("운영 품질 지표는 0 이상 100 이하여야 합니다.");
        }
    }
}
