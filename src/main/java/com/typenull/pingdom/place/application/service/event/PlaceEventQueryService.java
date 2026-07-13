package com.typenull.pingdom.place.application.service.event;

import com.typenull.pingdom.place.api.dto.event.PlaceEventDetailResponse;
import com.typenull.pingdom.place.api.dto.event.PlaceEventListItem;
import com.typenull.pingdom.place.api.dto.event.PlaceEventListResponse;
import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceEventQueryService {

    private static final int MAX_PAGE = 10_000;

    private final PlaceEventRepository placeEventRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PlaceEventListResponse listDiscoverableEvents(
            PlaceEventType eventType,
            LocalDateTime fromAt,
            LocalDateTime toAt,
            int page,
            int limit
    ) {
        if (fromAt != null && toAt != null && !toAt.isAfter(fromAt)) {
            throw new MapException(MapErrorCode.PLACE_EVENT_SEARCH_CONDITION_INVALID);
        }

        int safePage = Math.max(1, Math.min(page, MAX_PAGE));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        LocalDateTime now = LocalDateTime.now(clock);
        Page<PlaceEvent> events = placeEventRepository.findDiscoverableEvents(
                PlaceEventPublicationStatus.PUBLISHED,
                now,
                eventType,
                fromAt,
                toAt,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("startAt").ascending().and(Sort.by("id").ascending()))
        );
        List<PlaceEventListItem> items = events.getContent().stream()
                .map(event -> toListItem(event, now))
                .toList();

        return PlaceEventListResponse.of(
                items,
                safePage,
                safeLimit,
                events.getTotalElements(),
                events.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PlaceEventDetailResponse getDiscoverableEvent(Long eventId) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceEvent event = placeEventRepository.findByIdAndPublicationStatusAndEndAtAfter(
                        eventId,
                        PlaceEventPublicationStatus.PUBLISHED,
                        now
                )
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_EVENT_NOT_FOUND));

        return new PlaceEventDetailResponse(
                event.getId(),
                event.getPlace().getId(),
                event.getPlace().getName(),
                event.getPlace().getAddress(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getStartAt(),
                event.getEndAt(),
                event.scheduleStatusAt(now)
        );
    }

    private PlaceEventListItem toListItem(PlaceEvent event, LocalDateTime now) {
        return new PlaceEventListItem(
                event.getId(),
                event.getPlace().getId(),
                event.getPlace().getName(),
                event.getTitle(),
                event.getEventType(),
                event.getStartAt(),
                event.getEndAt(),
                event.scheduleStatusAt(now)
        );
    }
}
