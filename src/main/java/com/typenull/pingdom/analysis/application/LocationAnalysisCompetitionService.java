package com.typenull.pingdom.analysis.application;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/** 추천 장소 주변의 실제 map_place 경쟁업체를 보고서 데이터에 보강한다. */
@Service
public class LocationAnalysisCompetitionService {

    private static final double COMPETITOR_RADIUS_METERS = 100d;
    private static final double FACILITY_RADIUS_METERS = 1500d;
    private static final int MAX_RECOMMENDED_PLACES = 5;
    private static final int MAX_ANALYSIS_PLACES = 30;

    private final MapPlaceCoordinateQueryRepository placeRepository;

    public LocationAnalysisCompetitionService(MapPlaceCoordinateQueryRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

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
        return name != null && (name.contains("역") || name.contains("정류장")
                || name.contains("터미널") || name.contains("지하철") || name.contains("공항"));
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
