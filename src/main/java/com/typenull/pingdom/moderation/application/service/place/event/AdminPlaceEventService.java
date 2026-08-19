package com.typenull.pingdom.moderation.application.service.place.event;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;

import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventActionRequest;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventRequest;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventResponse;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventListItem;
import com.typenull.pingdom.moderation.api.dto.place.event.AdminPlaceEventListResponse;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPlaceEventService {

    private final PlaceEventRepository placeEventRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminPlaceEventListResponse list(String keyword, Long placeId, PlaceEventType eventType,
            PlaceEventPublicationStatus publicationStatus, PlaceEventScheduleStatus scheduleStatus,
            int page, int limit) {
        int safePage = Math.max(1, Math.min(page, 10_000));
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        LocalDateTime now = now();
        Page<PlaceEvent> result = placeEventRepository.findAdminEvents(
                normalizedKeyword != null, normalizedKeyword,
                placeId != null, placeId,
                eventType != null, eventType,
                publicationStatus != null, publicationStatus,
                scheduleStatus != null,
                scheduleStatus == PlaceEventScheduleStatus.UPCOMING,
                scheduleStatus == PlaceEventScheduleStatus.ONGOING,
                scheduleStatus == PlaceEventScheduleStatus.ENDED,
                now,
                PageRequest.of(safePage - 1, safeLimit, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        return new AdminPlaceEventListResponse(result.getContent().stream().map(event -> toListItem(event, now)).toList(),
                safePage, safeLimit, result.getTotalElements(), Math.max(result.getTotalPages(), 1), result.hasNext());
    }

    @Transactional(readOnly = true)
    public AdminPlaceEventListItem get(Long eventId) {
        PlaceEvent event = placeEventRepository.findById(eventId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_EVENT_NOT_FOUND));
        return toListItem(event, now());
    }

    private AdminPlaceEventListItem toListItem(PlaceEvent event, LocalDateTime now) {
        return new AdminPlaceEventListItem(event.getId(), event.getPlace().getId(), event.getPlace().getName(),
                event.getPlace().getAddress(), event.getTitle(), event.getDescription(), event.getEventType(),
                event.getPublicationStatus(), event.scheduleStatusAt(now), event.getStartAt(), event.getEndAt(),
                event.getCreatedAt(), event.getUpdatedAt());
    }

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
        return toResponse(event, now, "기간형 이벤트를 초안으로 등록했습니다.");
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
        LocalDateTime now = now();
        event.update(
                place,
                request.title().trim(),
                trimToNull(request.description()),
                request.eventType(),
                request.startAt(),
                request.endAt(),
                now
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
        return toResponse(event, now, "기간형 이벤트를 수정했습니다.");
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
        return toResponse(event, now, "기간형 이벤트를 공개했습니다.");
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
        LocalDateTime now = now();
        event.cancel(now);

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_EVENT_CANCELLED,
                AdminAuditTargetType.PLACE_EVENT,
                event.getId(),
                request.reason().trim(),
                beforeState,
                eventState(event)
        );
        return toResponse(event, now, "기간형 이벤트를 취소했습니다.");
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

    private AdminPlaceEventResponse toResponse(PlaceEvent event, LocalDateTime now, String message) {
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
                event.scheduleStatusAt(now),
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
