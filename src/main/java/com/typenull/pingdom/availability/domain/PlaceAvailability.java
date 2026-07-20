package com.typenull.pingdom.availability.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
        validatePeriod(startsAt, endsAt);
        validateCapacity(totalCapacity);
        PlaceAvailability availability = new PlaceAvailability();
        availability.merchantOwnerUserId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        availability.placeId = Objects.requireNonNull(placeId, "placeId must not be null");
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
        validatePeriod(startsAt, endsAt);
        validateCapacity(totalCapacity);
        int allocated = this.totalCapacity - this.remainingCapacity;
        if (totalCapacity < allocated) throw new IllegalStateException("배정된 인원보다 총 수용 인원을 줄일 수 없습니다.");
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
        if (quantity <= 0 || status != AvailabilityStatus.ACTIVE || remainingCapacity < quantity || !endsAt.isAfter(now)) {
            throw new IllegalStateException("예약 가능한 재고가 부족합니다.");
        }
        remainingCapacity -= quantity;
        updatedAt = now;
    }

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
