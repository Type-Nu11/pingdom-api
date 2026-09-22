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

/**
 * 장소 이벤트를 초안으로 만들고 행 잠금 아래 수정·공개·취소와 감사 기록을 반영합니다.
 * 초안만 수정 가능하고 종료 시각이 현재보다 이후인 초안만 공개할 수 있으며, 이미 시작된 이벤트의 공개도 허용합니다.
 */
@Service
@RequiredArgsConstructor
public class AdminPlaceEventService {

    private final PlaceEventRepository placeEventRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    /**
     * 검색어·장소·이벤트 종류·공개 및 일정 상태를 조합해 최신순 조회하고 같은 현재 시각으로 응답 일정 상태를 계산합니다.
     * page는 1~10,000, limit는 1~100으로 보정하며 빈 결과의 totalPages도 최소 1로 반환합니다.
     */
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

    /**
     * 종료가 시작보다 늦은 기간을 검증하고 연결 장소를 잠근 뒤 DRAFT 이벤트와 감사 기록을 함께 저장합니다.
     * 기간이 잘못되거나 장소가 없으면 거절하며 제목의 양끝 공백과 비어 있는 설명을 정규화해 새 이벤트를 반환합니다.
     */
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

    /**
     * 기간을 검증하고 이벤트를 잠가 DRAFT인 경우에만 내용·연결 장소·일정을 변경합니다.
     * 대상 이벤트나 장소가 없거나 이미 공개·취소 상태이면 거절하며 변경 전후 감사 기록을 같은 트랜잭션에 저장합니다.
     */
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

    /**
     * 이벤트를 잠그고 DRAFT이며 종료 시각이 현재보다 뒤일 때만 공개 상태로 전환합니다.
     * 이미 시작한 일정도 허용하며 공개 전이와 감사 기록을 함께 저장합니다. 대상 없음과 공개 불가 상태는 오류로 반환합니다.
     */
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

    /**
     * 취소 상태만 재취소를 거절합니다. 초안·공개 및 이미 종료된 일정도 취소 가능하며 예약·결제 환불을 연동하지 않습니다.
     */
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
