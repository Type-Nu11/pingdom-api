package com.typenull.pingdom.moderation.api.dto.place.event;

import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import java.time.LocalDateTime;

public record AdminPlaceEventListItem(
        Long eventId, Long placeId, String placeName, String placeAddress,
        String title, String description, PlaceEventType eventType,
        PlaceEventPublicationStatus publicationStatus, PlaceEventScheduleStatus scheduleStatus,
        LocalDateTime startAt, LocalDateTime endAt, LocalDateTime createdAt, LocalDateTime updatedAt
) {}
