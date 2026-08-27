package com.typenull.pingdom.place.domain.place.core;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 북마크 상태 전이를 보존해 기간별 순증가량을 재현합니다. */
@Entity
@Getter
@Table(name = "map_bookmark_trend_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapBookmarkTrendEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "origin_place_id", nullable = false, updatable = false)
    private Long originPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private MapBookmarkTrendEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    private MapBookmarkTrendEvent(
            Long userId,
            Long placeId,
            MapBookmarkTrendEventType eventType,
            LocalDateTime occurredAt
    ) {
        if (userId == null || userId <= 0 || placeId == null || placeId <= 0 || eventType == null || occurredAt == null) {
            throw new IllegalArgumentException("invalid bookmark trend event");
        }
        this.userId = userId;
        this.placeId = placeId;
        this.originPlaceId = placeId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
    }

    public static MapBookmarkTrendEvent added(Long userId, Long placeId, LocalDateTime occurredAt) {
        return new MapBookmarkTrendEvent(userId, placeId, MapBookmarkTrendEventType.ADDED, occurredAt);
    }

    public static MapBookmarkTrendEvent removed(Long userId, Long placeId, LocalDateTime occurredAt) {
        return new MapBookmarkTrendEvent(userId, placeId, MapBookmarkTrendEventType.REMOVED, occurredAt);
    }
}
