package com.typenull.pingdom.place.domain.event;

import com.typenull.pingdom.place.domain.place.MapPlace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "place_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "map_place_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_place_event_place")
    )
    private MapPlace place;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private PlaceEventType eventType;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PlaceEventPublicationStatus publicationStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PlaceEvent(
            MapPlace place,
            String title,
            String description,
            PlaceEventType eventType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime createdAt
    ) {
        validatePeriod(startAt, endAt);
        this.place = Objects.requireNonNull(place, "place must not be null");
        this.title = requireText(title, "title");
        this.description = description;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.startAt = startAt;
        this.endAt = endAt;
        this.publicationStatus = PlaceEventPublicationStatus.DRAFT;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
    }

    public static PlaceEvent create(
            MapPlace place,
            String title,
            String description,
            PlaceEventType eventType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime createdAt
    ) {
        return new PlaceEvent(place, title, description, eventType, startAt, endAt, createdAt);
    }

    public void update(
            MapPlace place,
            String title,
            String description,
            PlaceEventType eventType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime updatedAt
    ) {
        ensureDraft();
        validatePeriod(startAt, endAt);
        this.place = Objects.requireNonNull(place, "place must not be null");
        this.title = requireText(title, "title");
        this.description = description;
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public void publish(LocalDateTime publishedAt) {
        ensureDraft();
        LocalDateTime now = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        if (!endAt.isAfter(now)) {
            throw new IllegalStateException("종료된 이벤트는 공개할 수 없습니다.");
        }
        this.publicationStatus = PlaceEventPublicationStatus.PUBLISHED;
        this.updatedAt = now;
    }

    public void cancel(LocalDateTime cancelledAt) {
        if (publicationStatus == PlaceEventPublicationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 이벤트입니다.");
        }
        this.publicationStatus = PlaceEventPublicationStatus.CANCELLED;
        this.updatedAt = Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
    }

    public PlaceEventScheduleStatus scheduleStatusAt(LocalDateTime now) {
        LocalDateTime referenceTime = Objects.requireNonNull(now, "now must not be null");
        if (referenceTime.isBefore(startAt)) {
            return PlaceEventScheduleStatus.UPCOMING;
        }
        if (referenceTime.isBefore(endAt)) {
            return PlaceEventScheduleStatus.ONGOING;
        }
        return PlaceEventScheduleStatus.ENDED;
    }

    private void ensureDraft() {
        if (publicationStatus != PlaceEventPublicationStatus.DRAFT) {
            throw new IllegalStateException("초안 이벤트만 수정하거나 공개할 수 있습니다.");
        }
    }

    private static void validatePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("종료 시각은 시작 시각보다 이후여야 합니다.");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
