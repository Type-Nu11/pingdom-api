package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingStatusUpdateRequest;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.application.service.place.operating.PlaceCurrentOperatingState;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingScheduleInput;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** facade의 기존 트랜잭션과 capability 계약을 따라 점주 영업 상태·일정을 관리합니다. */
@Component
@RequiredArgsConstructor
class MerchantOwnerPlaceOperatingService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MerchantPlaceCapabilityPolicy capabilityPolicy;
    private final PlaceOperatingHoursEvaluator operatingHoursEvaluator;
    private final Clock clock;

    MerchantOwnerOperatingResponse getOperating(Long userId, Long placeId) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_VIEW);
        MapPlace place = findPlace(placeId);
        PlaceCurrentOperatingState current = operatingHoursEvaluator.evaluate(place);
        return new MerchantOwnerOperatingResponse(
                placeId,
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                current.currentlyOperating(),
                current.checkedAt(),
                regularHours(place),
                operatingExceptions(place),
                place.getPrimaryInformationSource(),
                place.getInformationVerificationStatus()
        );
    }

    MerchantOwnerOperatingResponse updateOperatingStatus(
            Long userId,
            Long placeId,
            MerchantOwnerOperatingStatusUpdateRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.SCHEDULE_MANAGE);
        if (request == null || request.operatingStatus() == null) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
        MapPlace place = findPlaceForUpdate(placeId);
        Instant checkedAt = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(checkedAt, clock.getZone());
        place.updateOperatingStatus(request.operatingStatus(), now);
        markOwnerSubmitted(place, now);
        PlaceCurrentOperatingState current = operatingHoursEvaluator.evaluate(place, checkedAt);
        return new MerchantOwnerOperatingResponse(
                placeId,
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                current.currentlyOperating(),
                current.checkedAt(),
                regularHours(place),
                operatingExceptions(place),
                place.getPrimaryInformationSource(),
                place.getInformationVerificationStatus()
        );
    }

    MerchantOwnerOperatingScheduleResponse updateOperatingSchedule(
            Long userId,
            Long placeId,
            PlaceOperatingScheduleInput request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.SCHEDULE_MANAGE);
        if (request == null) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
        MapPlace place = findPlaceForUpdate(placeId);
        Set<PlaceRegularOperatingHour> regularHours = toRegularHours(request.regularHours());
        List<PlaceOperatingException> exceptions = toExceptions(place, request.exceptions());
        validateSchedule(regularHours, exceptions);
        place.replaceOperatingSchedule(regularHours, exceptions);
        markOwnerSubmitted(place, LocalDateTime.now(clock));
        return new MerchantOwnerOperatingScheduleResponse(
                placeId,
                regularHours(place),
                operatingExceptions(place),
                "장소 영업시간 일정을 수정했습니다."
        );
    }

    private void requireCapability(Long userId, Long placeId, MerchantPlaceCapability capability) {
        capabilityPolicy.require(userId, placeId, capability);
    }

    private MapPlace findPlace(Long placeId) {
        return mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private MapPlace findPlaceForUpdate(Long placeId) {
        return mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    List<PlaceRegularOperatingHourResponse> regularHours(MapPlace place) {
        return place.currentRegularOperatingHours().stream()
                .sorted(Comparator.comparing(PlaceRegularOperatingHour::getDayOfWeek)
                        .thenComparing(PlaceRegularOperatingHour::getOpensAt))
                .map(hour -> new PlaceRegularOperatingHourResponse(
                        hour.getDayOfWeek(), hour.getOpensAt(), hour.getClosesAt()
                ))
                .toList();
    }

    List<PlaceOperatingExceptionResponse> operatingExceptions(MapPlace place) {
        return place.currentOperatingExceptions().stream()
                .sorted(Comparator.comparing(PlaceOperatingException::getExceptionDate))
                .map(exception -> new PlaceOperatingExceptionResponse(
                        exception.getExceptionDate(),
                        exception.isClosed(),
                        exception.currentHours().stream()
                                .sorted(Comparator.comparing(PlaceOperatingTimeRange::getOpensAt))
                                .map(hour -> new PlaceOperatingTimeRangeResponse(hour.getOpensAt(), hour.getClosesAt()))
                                .toList()
                ))
                .toList();
    }

    private Set<PlaceRegularOperatingHour> toRegularHours(
            Set<PlaceOperatingScheduleInput.RegularHour> requests
    ) {
        if (requests == null) {
            return Set.of();
        }
        return requests.stream()
                .map(request -> PlaceRegularOperatingHour.of(request.dayOfWeek(), request.opensAt(), request.closesAt()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<PlaceOperatingException> toExceptions(
            MapPlace place,
            Set<PlaceOperatingScheduleInput.ExceptionDay> requests
    ) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .sorted(Comparator.comparing(PlaceOperatingScheduleInput.ExceptionDay::date))
                .map(request -> {
                    if (request.closed()) {
                        return PlaceOperatingException.closed(place, request.date());
                    }
                    Set<PlaceOperatingTimeRange> hours = request.hours() == null
                            ? Set.of()
                            : request.hours().stream()
                            .map(this::toTimeRange)
                            .collect(java.util.stream.Collectors.toSet());
                    return PlaceOperatingException.customHours(place, request.date(), hours);
                })
                .toList();
    }

    private PlaceOperatingTimeRange toTimeRange(PlaceOperatingScheduleInput.TimeRange request) {
        return PlaceOperatingTimeRange.of(request.opensAt(), request.closesAt());
    }

    private void validateSchedule(Set<PlaceRegularOperatingHour> regularHours, List<PlaceOperatingException> exceptions) {
        if (regularHours.stream().anyMatch(hour -> hour.getDayOfWeek() == null
                || hour.getOpensAt() == null || hour.getClosesAt() == null
                || hour.getOpensAt().equals(hour.getClosesAt()))) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
        Set<LocalDate> dates = new HashSet<>();
        for (PlaceOperatingException exception : exceptions) {
            if (!dates.add(exception.getExceptionDate())
                    || (!exception.isClosed() && exception.currentHours().isEmpty())
                    || (exception.isClosed() && !exception.currentHours().isEmpty())
                    || exception.currentHours().stream().anyMatch(hour -> hour.getOpensAt().equals(hour.getClosesAt()))) {
                throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
        }
    }

    private void markOwnerSubmitted(MapPlace place, LocalDateTime now) {
        place.updateInformationVerification(
                PlaceInformationSourceType.MERCHANT_OWNER,
                PlaceInformationVerificationStatus.OWNER_SUBMITTED,
                null,
                null,
                now
        );
    }
}
