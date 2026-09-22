package com.typenull.pingdom.availability.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시간 구간의 총 수용량과 잔여 수용량을 보유하며 예약 배정량은 두 값의 차이로 계산.
 * 재고 변경의 동시성은 호출 서비스의 잠금 또는 엔티티 버전 검사에 의존하며, 사용자·상품 자격 조회는 호출자 책임.
 */
@Entity
@Getter
@Table(name = "place_availability")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceAvailability {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_owner_user_id", nullable = false)
    private Long merchantOwnerUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private AvailabilityProductType productType;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "remaining_capacity", nullable = false)
    private int remainingCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version @Column(nullable = false)
    private long version;

    public static PlaceAvailability create(Long ownerId, Long placeId, LocalDateTime startsAt,
            LocalDateTime endsAt, int totalCapacity, LocalDateTime now) {
        return create(ownerId, placeId, AvailabilityProductType.GENERAL, startsAt, endsAt, totalCapacity, now);
    }

    public static PlaceAvailability create(Long ownerId, Long placeId, AvailabilityProductType productType,
            LocalDateTime startsAt, LocalDateTime endsAt, int totalCapacity, LocalDateTime now) {
        return create(ownerId, placeId, null, productType, startsAt, endsAt, totalCapacity, now);
    }

    public static PlaceAvailability create(Long ownerId, Long placeId, Long productId,
            AvailabilityProductType productType, LocalDateTime startsAt, LocalDateTime endsAt,
            int totalCapacity, LocalDateTime now) {
        validatePeriod(startsAt, endsAt);
        validateCapacity(totalCapacity);
        PlaceAvailability availability = new PlaceAvailability();
        availability.merchantOwnerUserId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        availability.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
        availability.productId = productId;
        availability.productType = Objects.requireNonNull(productType, "productType must not be null");
        availability.startsAt = startsAt;
        availability.endsAt = endsAt;
        availability.totalCapacity = totalCapacity;
        availability.remainingCapacity = totalCapacity;
        availability.status = AvailabilityStatus.ACTIVE;
        availability.createdAt = Objects.requireNonNull(now, "now must not be null");
        availability.updatedAt = now;
        return availability;
    }

    public void update(LocalDateTime startsAt, LocalDateTime endsAt, int totalCapacity, LocalDateTime now) {
        update(productType, startsAt, endsAt, totalCapacity, now);
    }

    public void update(AvailabilityProductType productType, LocalDateTime startsAt, LocalDateTime endsAt,
            int totalCapacity, LocalDateTime now) {
        update(productId, productType, startsAt, endsAt, totalCapacity, now);
    }

    /**
     * 이미 배정된 수량을 보존하면서 총량과 잔여량을 함께 변경.
     * 배정량이 양수이면 상품·시간은 고정하고, 총량은 배정량 이상이어야 함.
     */
    public void update(Long productId, AvailabilityProductType productType, LocalDateTime startsAt,
            LocalDateTime endsAt, int totalCapacity, LocalDateTime now) {
        validatePeriod(startsAt, endsAt);
        validateCapacity(totalCapacity);
        int allocated = this.totalCapacity - this.remainingCapacity;
        if (totalCapacity < allocated) throw new IllegalStateException("배정된 인원보다 총 수용 인원을 줄일 수 없습니다.");
        AvailabilityProductType nextProductType = Objects.requireNonNull(productType, "productType must not be null");
        if (allocated > 0 && (!Objects.equals(this.productId, productId) || this.productType != nextProductType)) {
            throw new IllegalStateException("예약이 존재하는 슬롯의 상품은 변경할 수 없습니다.");
        }
        if (allocated > 0 && (!this.startsAt.equals(startsAt) || !this.endsAt.equals(endsAt))) {
            throw new IllegalStateException("예약이 존재하는 슬롯의 일시는 변경할 수 없습니다.");
        }
        this.productId = productId;
        this.productType = nextProductType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.totalCapacity = totalCapacity;
        this.remainingCapacity = totalCapacity - allocated;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void deactivate(LocalDateTime now) {
        status = AvailabilityStatus.INACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void activate(LocalDateTime now) {
        if (!endsAt.isAfter(now)) throw new IllegalStateException("종료된 슬롯은 활성화할 수 없습니다.");
        status = AvailabilityStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void reserve(int quantity, LocalDateTime now) {
        if (quantity <= 0 || status != AvailabilityStatus.ACTIVE || remainingCapacity < quantity || !startsAt.isAfter(now)) {
            throw new IllegalStateException("예약 가능한 재고가 부족합니다.");
        }
        remainingCapacity -= quantity;
        updatedAt = now;
    }

    /**
     * 기존 예약 해제량을 잔여량에 돌려놓되 총 수용량을 넘는 반환은 거절.
     * 비활성·종료 슬롯에도 반환은 허용하며 동일 반환 요청의 중복 여부는 호출자가 보장해야 함.
     */
    public void release(int quantity, LocalDateTime now) {
        if (quantity <= 0 || remainingCapacity + quantity > totalCapacity) {
            throw new IllegalStateException("복구할 재고가 올바르지 않습니다.");
        }
        remainingCapacity += quantity;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private static void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 이후여야 합니다.");
        }
    }

    private static void validateCapacity(int totalCapacity) {
        if (totalCapacity <= 0) throw new IllegalArgumentException("총 수용 인원은 1 이상이어야 합니다.");
    }
}
