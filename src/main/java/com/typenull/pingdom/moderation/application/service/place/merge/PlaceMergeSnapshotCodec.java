package com.typenull.pingdom.moderation.application.service.place.merge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminPlaceServiceSupport;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot;
import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot.OperatingExceptionSnapshot;
import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot.OperatingTimeRangeSnapshot;
import com.typenull.pingdom.moderation.domain.place.PlaceMergeSnapshot.RegularOperatingHourSnapshot;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 장소 필드 추출과 기존 JSON 직렬화 계약을 한 경계에서 관리하며 ObjectMapper 설정은 변경하지 않음. */
@Component
@RequiredArgsConstructor
public class PlaceMergeSnapshotCodec {
    private final ObjectMapper objectMapper;

    public PlaceMergeSnapshot capture(MapPlace place) {
        return new PlaceMergeSnapshot(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getRoadAddress(),
                place.getJibunAddress(),
                place.getPostalCode(),
                place.getGeocodingSource(),
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                place.getCategory(),
                place.getImageUrl(),
                place.getKakaoPlaceId(),
                place.getLatitude(),
                place.getLongitude(),
                place.getUserId(),
                place.getRegistrant(),
                place.currentPhotoCount(),
                place.getEnglishName(),
                place.getTouristSummary(),
                AdminPlaceServiceSupport.normalizeTouristCategories(place.currentTouristCategories()),
                place.currentRegularOperatingHours().stream()
                        .map(hour -> new RegularOperatingHourSnapshot(
                                hour.getDayOfWeek(), hour.getOpensAt(), hour.getClosesAt()))
                        .toList(),
                place.currentOperatingExceptions().stream()
                        .map(exception -> new OperatingExceptionSnapshot(
                                exception.getExceptionDate(),
                                exception.isClosed(),
                                exception.currentHours().stream()
                                        .map(hour -> new OperatingTimeRangeSnapshot(hour.getOpensAt(), hour.getClosesAt()))
                                        .toList()))
                        .toList()
        );
    }

    public PlaceMergeSnapshot read(String value) {
        try {
            return objectMapper.readValue(value, PlaceMergeSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
        }
    }

    public String write(PlaceMergeSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
        }
    }
}
