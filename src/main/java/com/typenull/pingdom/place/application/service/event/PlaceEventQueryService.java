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
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시된 미종료 장소 행사를 검색하고 조회 시점 기준의 진행 상태를 반환.
 * 검색 기간은 시작보다 종료가 늦어야 하며 응답 날짜에는 UTC offset을 붙임.
 */
@Service
@RequiredArgsConstructor
public class PlaceEventQueryService {

    private static final int MAX_PAGE = 10_000;

    private final PlaceEventRepository placeEventRepository;
    private final Clock clock;

    /**
     * 종료 전 공개 행사를 선택적 유형·기간 겹침 조건으로 조회하고 시작 시각·ID 오름차순으로 반환.
     * 양쪽 기간이 주어지면 종료가 시작보다 뒤여야 하며 페이지는 1~10,000, 크기는 1~100으로 보정.
     * 아직 시작하지 않은 행사도 포함하고 진행 상태는 같은 조회 시각으로 계산.
     */
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

    /**
     * 공개 상태이고 종료 시각이 현재보다 뒤인 행사의 장소 정보와 상세를 반환.
     * 해당 조건에 맞지 않으면 PLACE_EVENT_NOT_FOUND로 처리하며 저장된 행사 시각에는 UTC 오프셋을 붙임.
     */
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
                event.getStartAt().atOffset(ZoneOffset.UTC),
                event.getEndAt().atOffset(ZoneOffset.UTC),
                event.scheduleStatusAt(now).name()
        );
    }

    private PlaceEventListItem toListItem(PlaceEvent event, LocalDateTime now) {
        return new PlaceEventListItem(
                event.getId(),
                event.getPlace().getId(),
                event.getPlace().getName(),
                event.getTitle(),
                event.getEventType(),
                event.getStartAt().atOffset(ZoneOffset.UTC),
                event.getEndAt().atOffset(ZoneOffset.UTC),
                event.scheduleStatusAt(now).name()
        );
    }
}
