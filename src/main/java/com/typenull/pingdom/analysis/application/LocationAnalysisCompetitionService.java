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
    private static final int MAX_RECOMMENDED_PLACES = 5;
    private static final int MAX_COMPETITORS_PER_PLACE = 10;

    private final MapPlaceCoordinateQueryRepository placeRepository;

    public LocationAnalysisCompetitionService(MapPlaceCoordinateQueryRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public LocationAnalysisContent enrich(LocationAnalysisContent content, String category) {
        if (content == null || content.recommendedPlaces().isEmpty()) {
            return content;
        }

        String normalizedCategory = PlaceCategoryPolicy.normalize(category);
        Map<Long, LocationAnalysisContent.Facility> competitors = new LinkedHashMap<>();
        content.recommendedPlaces().stream()
                .limit(MAX_RECOMMENDED_PLACES)
                .filter(place -> place.latitude() != null && place.longitude() != null)
                .forEach(place -> placeRepository.findNearbyPlacesByCategory(
                                place.latitude(), place.longitude(), normalizedCategory,
                                COMPETITOR_RADIUS_METERS, PageRequest.of(0, MAX_COMPETITORS_PER_PLACE))
                        .forEach(candidate -> competitors.putIfAbsent(
                                candidate.getPlaceId(),
                                new LocationAnalysisContent.Facility(
                                        candidate.getName(), candidate.getCategory(), candidate.getDistanceMeters(),
                                        candidate.getAddress(), "요청 업종과 동일한 map_place 장소"
                                )
                        )));

        List<LocationAnalysisContent.Facility> ordered = competitors.values().stream()
                .sorted(Comparator.comparing(
                        LocationAnalysisContent.Facility::distanceMeters,
                        Comparator.nullsLast(Double::compareTo)))
                .toList();
        return content.withNearbyCompetitors(ordered);
    }
}
