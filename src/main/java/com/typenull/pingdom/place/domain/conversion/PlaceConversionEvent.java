package com.typenull.pingdom.place.domain.conversion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "place_conversion_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceConversionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_conversion_event_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "map_place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversion_type", nullable = false, length = 20)
    private PlaceConversionEventType conversionType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "deduplication_key", nullable = false, unique = true, length = 200)
    private String deduplicationKey;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PlaceConversionEvent(
            Long userId,
            Long placeId,
            PlaceConversionEventType conversionType,
            Long sourceId,
            String deduplicationKey,
            LocalDateTime occurredAt,
            LocalDateTime createdAt
    ) {
        this.userId = requirePositive(userId, "userId");
        this.placeId = requirePositive(placeId, "placeId");
        this.conversionType = Objects.requireNonNull(conversionType, "conversionType must not be null");
        this.sourceId = requirePositive(sourceId, "sourceId");
        this.deduplicationKey = requireText(deduplicationKey, "deduplicationKey");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static PlaceConversionEvent create(
            Long userId,
            Long placeId,
            PlaceConversionEventType conversionType,
            Long sourceId,
            String deduplicationKey,
            LocalDateTime occurredAt,
            LocalDateTime createdAt
    ) {
        return new PlaceConversionEvent(
                userId, placeId, conversionType, sourceId, deduplicationKey, occurredAt, createdAt
        );
    }

    private static Long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException(name + " must contain 1-200 characters");
        }
        return value;
    }
}
