package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 추천 장소 주변의 실제 map_place 경쟁업체를 보고서 데이터에 보강. */
@Service
public class LocationAnalysisCompetitionService {

    private static final double COMPETITOR_RADIUS_METERS = 100d;
    private static final double FACILITY_RADIUS_METERS = 1500d;
    private static final int MAX_RECOMMENDED_PLACES = 5;
    private static final int MAX_ANALYSIS_PLACES = 100;
    private static final Pattern STATION_NAME = Pattern.compile(".+역(?:\\s*\\([^)]*\\)|\\s+\\d+호선)?$");

    private final MapPlaceCoordinateQueryRepository placeRepository;

    public LocationAnalysisCompetitionService(MapPlaceCoordinateQueryRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * 상위 다섯 추천 중 좌표가 있는 첫 장소를 기준으로 반경 1,500m의 최대 100개 장소를 한 번 조회.
     * 동일 업종의 100m 이내 장소를 경쟁업체로 분류하고, 교통시설은 시설명 형태로 추정.
     * 기준 좌표 누락 시 원래 응답 유지. 주변 검색 범위는 기준 장소 한 곳으로 제한.
     */
    public LocationAnalysisContent enrich(LocationAnalysisContent content, String category) {
        if (content == null || content.recommendedPlaces().isEmpty()) {
            return content;
        }

        String normalizedCategory = PlaceCategoryPolicy.normalize(category);
        LocationAnalysisContent.RecommendedPlace anchor = content.recommendedPlaces().stream()
                .limit(MAX_RECOMMENDED_PLACES)
                .filter(place -> place.latitude() != null && place.longitude() != null)
                .findFirst()
                .orElse(null);
        if (anchor == null) {
            return content;
        }

        Map<Long, LocationAnalysisContent.Facility> competitors = new LinkedHashMap<>();
        Map<Long, LocationAnalysisContent.Facility> convenience = new LinkedHashMap<>();
        Map<Long, LocationAnalysisContent.Facility> transport = new LinkedHashMap<>();
        placeRepository.findNearbyPlacesForAnalysis(
                        anchor.latitude(), anchor.longitude(), FACILITY_RADIUS_METERS,
                        PageRequest.of(0, MAX_ANALYSIS_PLACES))
                .forEach(candidate -> {
                    LocationAnalysisContent.Facility facility = new LocationAnalysisContent.Facility(
                            candidate.getName(), candidate.getCategory(), candidate.getDistanceMeters(),
                            candidate.getAddress(), "분석 기준 좌표 주변의 공개 map_place 장소"
                    );
                    Long placeId = candidate.getPlaceId();
                    String candidateCategory = candidate.getCategory();
                    if (normalizedCategory != null && normalizedCategory.equalsIgnoreCase(candidateCategory)
                            && candidate.getDistanceMeters() != null
                            && candidate.getDistanceMeters() <= COMPETITOR_RADIUS_METERS) {
                        competitors.putIfAbsent(placeId, facility);
                    } else if (isTransport(candidate.getName())) {
                        transport.putIfAbsent(placeId, facility);
                    } else if (normalizedCategory == null || !normalizedCategory.equalsIgnoreCase(candidateCategory)) {
                        convenience.putIfAbsent(placeId, facility);
                    }
                });

        return content.withNearbyPlaces(
                sortByDistance(competitors), sortByDistance(convenience), sortByDistance(transport)
        );
    }

    private boolean isTransport(String name) {
        if (name == null) {
            return false;
        }
        String facilityName = name.trim();
        // '석촌역점' 같은 일반 매장은 제외하고 역명 또는 교통시설 명칭으로 끝나는 이름만 인정.
        return STATION_NAME.matcher(facilityName).matches() || facilityName.endsWith("정류장")
                || facilityName.endsWith("터미널") || facilityName.endsWith("공항");
    }

    private List<LocationAnalysisContent.Facility> sortByDistance(
            Map<Long, LocationAnalysisContent.Facility> facilities
    ) {
        return facilities.values().stream()
                .sorted(Comparator.comparing(
                        LocationAnalysisContent.Facility::distanceMeters,
                        Comparator.nullsLast(Double::compareTo)))
                .toList();
    }
}
