package com.typenull.pingdom.moderation.application.service.place.operating;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminPlaceServiceSupport;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingExceptionRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingTimeRangeRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceRegularOperatingHourRequest;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 정규·예외 영업시간을 검증한 뒤 장소 잠금 아래 전체 교체하고 감사 기록을 남김.
 * null 목록은 빈 목록으로 처리. 자정을 넘는 구간은 다음 날까지 나누어 겹침을 검사하며 맞닿는 경계는 허용.
 * 시작·종료가 같은 시각은 24시간 영업으로 해석하지 않고 거절.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlaceOperatingScheduleService {
    private static final long NANOS_PER_DAY = 24L * 60 * 60 * 1_000_000_000;

    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService adminAuditLogService;

    /**
     * 사유와 정규·예외 일정의 중복·겹침을 검증한 뒤 장소를 잠가 전체 일정을 교체하고 감사 기록을 함께 저장.
     * null 목록은 빈 일정으로 반영하며 이 호출 경로는 기존 정규 휴게시간도 빈 집합으로 교체.
     * 요청 검증 실패 또는 장소 없음은 오류이며 자정 통과 구간의 겹침 규칙은 클래스 설명을 따름.
     */
    @Transactional
    public AdminMapPlaceOperatingScheduleUpdateResponse updatePlaceOperatingSchedule(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceOperatingScheduleUpdateRequest request
    ) {
        validateOperatingScheduleRequest(request);

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        Map<String, Object> beforeState = AdminPlaceServiceSupport.operatingScheduleState(mapPlace);

        mapPlace.replaceOperatingSchedule(
                toRegularOperatingHours(request.regularHours()),
                toOperatingExceptions(mapPlace, request.exceptions())
        );

        Map<String, Object> afterState = AdminPlaceServiceSupport.operatingScheduleState(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_OPERATING_SCHEDULE_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );

        log.info(
                "Admin updated place operating schedule. adminUserId={}, placeId={}, regularHourCount={}, exceptionCount={}",
                adminUserId,
                placeId,
                mapPlace.currentRegularOperatingHours().size(),
                mapPlace.currentOperatingExceptions().size()
        );

        return new AdminMapPlaceOperatingScheduleUpdateResponse(
                mapPlace.getId(),
                AdminPlaceServiceSupport.regularHours(mapPlace),
                AdminPlaceServiceSupport.operatingExceptions(mapPlace),
                "장소 영업시간 일정을 수정했습니다."
        );
    }

    private void validateOperatingScheduleRequest(AdminMapPlaceOperatingScheduleUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }

        Set<AdminMapPlaceRegularOperatingHourRequest> regularHours = request.regularHours() == null
                ? Set.of()
                : request.regularHours();
        Set<AdminMapPlaceOperatingExceptionRequest> exceptions = request.exceptions() == null
                ? Set.of()
                : request.exceptions();

        validateRegularOperatingHours(regularHours);
        validateOperatingExceptions(exceptions);
    }

    private void validateRegularOperatingHours(Set<AdminMapPlaceRegularOperatingHourRequest> regularHours) {
        Map<DayOfWeek, List<TimeSegment>> segmentsByDay = new EnumMap<>(DayOfWeek.class);
        Set<PlaceRegularOperatingHour> distinctHours = new HashSet<>();

        for (AdminMapPlaceRegularOperatingHourRequest hour : regularHours) {
            if (hour == null || hour.dayOfWeek() == null) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            validateTimeRange(hour.opensAt(), hour.closesAt());

            PlaceRegularOperatingHour regularOperatingHour = PlaceRegularOperatingHour.of(
                    hour.dayOfWeek(),
                    hour.opensAt(),
                    hour.closesAt()
            );
            if (!distinctHours.add(regularOperatingHour)) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            addWeeklySegments(segmentsByDay, hour.dayOfWeek(), hour.opensAt(), hour.closesAt());
        }

        segmentsByDay.values().forEach(this::validateNoOverlap);
    }

    private void validateOperatingExceptions(Set<AdminMapPlaceOperatingExceptionRequest> exceptions) {
        Set<LocalDate> dates = new HashSet<>();
        Map<LocalDate, List<TimeSegment>> segmentsByDate = new LinkedHashMap<>();
        for (AdminMapPlaceOperatingExceptionRequest exception : exceptions) {
            if (exception == null || exception.date() == null || !dates.add(exception.date())) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }

            Set<AdminMapPlaceOperatingTimeRangeRequest> hours = exception.hours() == null
                    ? Set.of()
                    : exception.hours();
            if (exception.closed() && !hours.isEmpty()) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            if (!exception.closed() && hours.isEmpty()) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            // 휴무일을 하루 전체 구간으로 넣어 전날 시작한 심야 영업과의 충돌도 같은 검사에서 탐지.
            if (exception.closed()) {
                segmentsByDate.computeIfAbsent(exception.date(), ignored -> new ArrayList<>())
                        .add(new TimeSegment(0, NANOS_PER_DAY));
                continue;
            }

            Set<PlaceOperatingTimeRange> distinctHours = new HashSet<>();
            for (AdminMapPlaceOperatingTimeRangeRequest hour : hours) {
                if (hour == null) {
                    throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
                }
                validateTimeRange(hour.opensAt(), hour.closesAt());
                PlaceOperatingTimeRange timeRange = PlaceOperatingTimeRange.of(hour.opensAt(), hour.closesAt());
                if (!distinctHours.add(timeRange)) {
                    throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
                }
                addExceptionSegments(segmentsByDate, exception.date(), hour.opensAt(), hour.closesAt());
            }
        }

        segmentsByDate.values().forEach(this::validateNoOverlap);
    }

    private void validateTimeRange(LocalTime opensAt, LocalTime closesAt) {
        if (opensAt == null || closesAt == null || opensAt.equals(closesAt)) {
            throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
    }

    private void addWeeklySegments(
            Map<DayOfWeek, List<TimeSegment>> segmentsByDay,
            DayOfWeek dayOfWeek,
            LocalTime opensAt,
            LocalTime closesAt
    ) {
        long startsAt = opensAt.toNanoOfDay();
        long endsAt = closesAt.toNanoOfDay();
        if (startsAt < endsAt) {
            segmentsByDay.computeIfAbsent(dayOfWeek, ignored -> new ArrayList<>())
                    .add(new TimeSegment(startsAt, endsAt));
            return;
        }

        segmentsByDay.computeIfAbsent(dayOfWeek, ignored -> new ArrayList<>())
                .add(new TimeSegment(startsAt, NANOS_PER_DAY));
        segmentsByDay.computeIfAbsent(dayOfWeek.plus(1), ignored -> new ArrayList<>())
                .add(new TimeSegment(0, endsAt));
    }

    private void addExceptionSegments(
            Map<LocalDate, List<TimeSegment>> segmentsByDate,
            LocalDate date,
            LocalTime opensAt,
            LocalTime closesAt
    ) {
        long startsAt = opensAt.toNanoOfDay();
        long endsAt = closesAt.toNanoOfDay();
        if (startsAt < endsAt) {
            segmentsByDate.computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(new TimeSegment(startsAt, endsAt));
            return;
        }

        segmentsByDate.computeIfAbsent(date, ignored -> new ArrayList<>())
                .add(new TimeSegment(startsAt, NANOS_PER_DAY));
        segmentsByDate.computeIfAbsent(date.plusDays(1), ignored -> new ArrayList<>())
                .add(new TimeSegment(0, endsAt));
    }

    private void validateNoOverlap(List<TimeSegment> segments) {
        List<TimeSegment> orderedSegments = segments.stream()
                .sorted(Comparator.comparingLong(TimeSegment::startsAt)
                        .thenComparingLong(TimeSegment::endsAt))
                .toList();
        for (int index = 1; index < orderedSegments.size(); index++) {
            TimeSegment previous = orderedSegments.get(index - 1);
            TimeSegment current = orderedSegments.get(index);
            if (current.startsAt() < previous.endsAt()) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
        }
    }

    private Set<PlaceRegularOperatingHour> toRegularOperatingHours(
            Set<AdminMapPlaceRegularOperatingHourRequest> regularHours
    ) {
        if (regularHours == null || regularHours.isEmpty()) {
            return Set.of();
        }
        Set<PlaceRegularOperatingHour> results = new HashSet<>();
        regularHours.forEach(hour -> results.add(PlaceRegularOperatingHour.of(
                hour.dayOfWeek(),
                hour.opensAt(),
                hour.closesAt()
        )));
        return results;
    }

    private List<PlaceOperatingException> toOperatingExceptions(
            MapPlace mapPlace,
            Set<AdminMapPlaceOperatingExceptionRequest> exceptions
    ) {
        if (exceptions == null || exceptions.isEmpty()) {
            return List.of();
        }
        return exceptions.stream()
                .sorted(Comparator.comparing(AdminMapPlaceOperatingExceptionRequest::date))
                .map(exception -> {
                    if (exception.closed()) {
                        return PlaceOperatingException.closed(mapPlace, exception.date());
                    }
                    Set<PlaceOperatingTimeRange> hours = new HashSet<>();
                    exception.hours().forEach(hour -> hours.add(PlaceOperatingTimeRange.of(
                            hour.opensAt(),
                            hour.closesAt()
                    )));
                    return PlaceOperatingException.customHours(mapPlace, exception.date(), hours);
                })
                .toList();
    }

    private record TimeSegment(long startsAt, long endsAt) {
    }
}
