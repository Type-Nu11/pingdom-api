package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventActionRequest;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventRequest;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventResponse;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPlaceEventService {

    private final PlaceEventRepository placeEventRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional
    public AdminPlaceEventResponse create(Long adminUserId, AdminPlaceEventRequest request) {
        validatePeriod(request);
        MapPlace place = findPlace(request.placeId());
        LocalDateTime now = now();
        PlaceEvent event = placeEventRepository.save(PlaceEvent.create(
                place,
                request.title().trim(),
                trimToNull(request.description()),
                request.eventType(),
                request.startAt(),
                request.endAt(),
                now
        ));

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_EVENT_CREATED,
                AdminAuditTargetType.PLACE_EVENT,
                event.getId(),
                request.reason().trim(),
                null,
                eventState(event)
        );
        return toResponse(event, "기간형 이벤트를 초안으로 등록했습니다.");
    }

    @Transactional
    public AdminPlaceEventResponse update(Long adminUserId, Long eventId, AdminPlaceEventRequest request) {
        validatePeriod(request);
        PlaceEvent event = findEventForUpdate(eventId);
        if (event.getPublicationStatus() != PlaceEventPublicationStatus.DRAFT) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_UPDATE_NOT_ALLOWED);
        }
        MapPlace place = findPlace(request.placeId());
        Map<String, Object> beforeState = eventState(event);
        event.update(
                place,
                request.title().trim(),
                trimToNull(request.description()),
                request.eventType(),
                request.startAt(),
                request.endAt(),
                now()
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_EVENT_UPDATED,
                AdminAuditTargetType.PLACE_EVENT,
                event.getId(),
                request.reason().trim(),
                beforeState,
                eventState(event)
        );
        return toResponse(event, "기간형 이벤트를 수정했습니다.");
    }

    @Transactional
    public AdminPlaceEventResponse publish(
            Long adminUserId,
            Long eventId,
            AdminPlaceEventActionRequest request
    ) {
        PlaceEvent event = findEventForUpdate(eventId);
        LocalDateTime now = now();
        if (event.getPublicationStatus() != PlaceEventPublicationStatus.DRAFT || !event.getEndAt().isAfter(now)) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_PUBLISH_NOT_ALLOWED);
        }
        Map<String, Object> beforeState = eventState(event);
        event.publish(now);

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_EVENT_PUBLISHED,
                AdminAuditTargetType.PLACE_EVENT,
                event.getId(),
                request.reason().trim(),
                beforeState,
                eventState(event)
        );
        return toResponse(event, "기간형 이벤트를 공개했습니다.");
    }

    @Transactional
    public AdminPlaceEventResponse cancel(
            Long adminUserId,
            Long eventId,
            AdminPlaceEventActionRequest request
    ) {
        PlaceEvent event = findEventForUpdate(eventId);
        if (event.getPublicationStatus() == PlaceEventPublicationStatus.CANCELLED) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_CANCEL_NOT_ALLOWED);
        }
        Map<String, Object> beforeState = eventState(event);
        event.cancel(now());

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_EVENT_CANCELLED,
                AdminAuditTargetType.PLACE_EVENT,
                event.getId(),
                request.reason().trim(),
                beforeState,
                eventState(event)
        );
        return toResponse(event, "기간형 이벤트를 취소했습니다.");
    }

    private void validatePeriod(AdminPlaceEventRequest request) {
        if (request == null
                || request.startAt() == null
                || request.endAt() == null
                || !request.endAt().isAfter(request.startAt())) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_INVALID_PERIOD);
        }
    }

    private MapPlace findPlace(Long placeId) {
        return mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceEvent findEventForUpdate(Long eventId) {
        return placeEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_EVENT_NOT_FOUND));
    }

    private AdminPlaceEventResponse toResponse(PlaceEvent event, String message) {
        return new AdminPlaceEventResponse(
                event.getId(),
                event.getPlace().getId(),
                event.getPlace().getName(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getStartAt(),
                event.getEndAt(),
                event.getPublicationStatus(),
                message
        );
    }

    private Map<String, Object> eventState(PlaceEvent event) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", event.getPlace().getId());
        state.put("title", event.getTitle());
        state.put("description", event.getDescription());
        state.put("eventType", event.getEventType());
        state.put("startAt", event.getStartAt());
        state.put("endAt", event.getEndAt());
        state.put("publicationStatus", event.getPublicationStatus());
        return state;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
