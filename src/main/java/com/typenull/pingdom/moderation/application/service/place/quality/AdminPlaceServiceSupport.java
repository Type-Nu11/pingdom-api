package com.typenull.pingdom.moderation.application.service.place.quality;

import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.util.StringUtils;

/** 관리자 장소 서비스들이 공유하는 순수 상태 변환·입력 정규화 함수 모음이다. */
public final class AdminPlaceServiceSupport {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    private AdminPlaceServiceSupport() {
    }

    public static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static Point toPoint(double latitude, double longitude) {
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }

    public static Set<TouristCategory> normalizeTouristCategories(Set<TouristCategory> categories) {
        Set<TouristCategory> normalized = EnumSet.noneOf(TouristCategory.class);
        if (categories != null) {
            normalized.addAll(categories);
        }
        return normalized;
    }

    public static Map<String, Object> placeState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("name", place.getName());
        state.put("address", place.getAddress());
        state.put("roadAddress", place.getRoadAddress());
        state.put("jibunAddress", place.getJibunAddress());
        state.put("postalCode", place.getPostalCode());
        state.put("geocodingSource", place.getGeocodingSource());
        state.put("operatingStatus", place.getOperatingStatus());
        state.put("operatingStatusCheckedAt", place.getOperatingStatusCheckedAt());
        state.put("discoveryStatus", place.getDiscoveryStatus());
        state.put("regularHours", regularHours(place));
        state.put("operatingExceptions", operatingExceptions(place));
        state.put("category", place.getCategory());
        state.put("englishName", place.getEnglishName());
        state.put("touristSummary", place.getTouristSummary());
        state.put("touristCategories", normalizeTouristCategories(place.currentTouristCategories()));
        state.put("kakaoPlaceId", place.getKakaoPlaceId());
        state.put("latitude", place.getLatitude());
        state.put("longitude", place.getLongitude());
        state.put("userId", place.getUserId());
        state.put("registrant", place.getRegistrant());
        state.put("photoCount", place.currentPhotoCount());
        return state;
    }

    public static Map<String, Object> geocodingState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("address", place.getAddress());
        state.put("roadAddress", place.getRoadAddress());
        state.put("jibunAddress", place.getJibunAddress());
        state.put("postalCode", place.getPostalCode());
        state.put("geocodingSource", place.getGeocodingSource());
        state.put("latitude", place.getLatitude());
        state.put("longitude", place.getLongitude());
        return state;
    }

    public static Map<String, Object> touristInfoState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("englishName", place.getEnglishName());
        state.put("touristSummary", place.getTouristSummary());
        state.put("touristCategories", normalizeTouristCategories(place.currentTouristCategories()));
        return state;
    }

    public static Map<String, Object> operatingStatusState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("operatingStatus", place.getOperatingStatus());
        state.put("operatingStatusCheckedAt", place.getOperatingStatusCheckedAt());
        return state;
    }

    public static Map<String, Object> discoveryStatusState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("discoveryStatus", place.getDiscoveryStatus());
        return state;
    }

    public static Map<String, Object> informationEvidenceState(PlaceInformationEvidence evidence) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("evidenceId", evidence.getId());
        state.put("placeId", evidence.getPlace().getId());
        state.put("sourceType", evidence.getSourceType());
        state.put("evidenceType", evidence.getEvidenceType());
        state.put("verificationStatus", evidence.getVerificationStatus());
        state.put("submittedByUserId", evidence.getSubmittedByUserId());
        state.put("reviewedByAdminUserId", evidence.getReviewedByAdminUserId());
        state.put("submittedAt", evidence.getSubmittedAt());
        state.put("reviewedAt", evidence.getReviewedAt());
        state.put("updatedAt", evidence.getUpdatedAt());
        return state;
    }

    public static Map<String, Object> operatingScheduleState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("regularHours", regularHours(place));
        state.put("operatingExceptions", operatingExceptions(place));
        return state;
    }

    public static List<PlaceRegularOperatingHourResponse> regularHours(MapPlace place) {
        return place.currentRegularOperatingHours().stream()
                .sorted(Comparator.comparing(PlaceRegularOperatingHour::getDayOfWeek)
                        .thenComparing(PlaceRegularOperatingHour::getOpensAt)
                        .thenComparing(PlaceRegularOperatingHour::getClosesAt))
                .map(hour -> new PlaceRegularOperatingHourResponse(
                        hour.getDayOfWeek(),
                        hour.getOpensAt(),
                        hour.getClosesAt()
                ))
                .toList();
    }

    public static List<PlaceOperatingExceptionResponse> operatingExceptions(MapPlace place) {
        return place.currentOperatingExceptions().stream()
                .map(exception -> new PlaceOperatingExceptionResponse(
                        exception.getExceptionDate(),
                        exception.isClosed(),
                        exception.currentHours().stream()
                                .sorted(Comparator.comparing(PlaceOperatingTimeRange::getOpensAt)
                                        .thenComparing(PlaceOperatingTimeRange::getClosesAt))
                                .map(hour -> new PlaceOperatingTimeRangeResponse(
                                        hour.getOpensAt(),
                                        hour.getClosesAt()
                                ))
                                .toList()
                ))
                .toList();
    }
}
