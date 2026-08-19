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
@Table(name = "verified_boost_execution")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** 검증된 부스트 상품의 집행 기간과 시점별 유효 상태를 관리합니다. */
public class VerifiedBoostExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "selection_id", nullable = false, unique = true)
    private Long selectionId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerifiedBoostExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static VerifiedBoostExecution start(MerchantVerifiedBoostSelection selection, int durationDays,
            LocalDateTime now) {
        if (durationDays < 1 || durationDays > 365) {
            throw new IllegalArgumentException("Boost 집행 기간이 올바르지 않습니다.");
        }
        VerifiedBoostExecution execution = new VerifiedBoostExecution();
        execution.selectionId = Objects.requireNonNull(selection.getId());
        execution.productId = Objects.requireNonNull(selection.getProductId());
        execution.merchantOwnerUserId = Objects.requireNonNull(selection.getMerchantOwnerUserId());
        execution.placeId = Objects.requireNonNull(selection.getPlaceId());
        execution.status = VerifiedBoostExecutionStatus.ACTIVE;
        execution.startedAt = Objects.requireNonNull(now);
        execution.endsAt = now.plusDays(durationDays);
        return execution;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return status == VerifiedBoostExecutionStatus.ACTIVE
                && !startedAt.isAfter(now)
                && endsAt.isAfter(now);
    }

    public VerifiedBoostExecutionStatus effectiveStatusAt(LocalDateTime now) {
        if (status == VerifiedBoostExecutionStatus.ACTIVE && !endsAt.isAfter(now)) {
            return VerifiedBoostExecutionStatus.EXPIRED;
        }
        return status;
    }

    public void stop(LocalDateTime now) {
        if (!isActiveAt(now)) {
            throw new IllegalStateException("활성 상태의 Boost 집행만 중단할 수 있습니다.");
        }
        status = VerifiedBoostExecutionStatus.STOPPED;
        stoppedAt = now;
    }
}
