package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRecommendationConversionRepository extends JpaRepository<PlaceRecommendationConversion, Long> {

    boolean existsByUserIdAndPlaceIdAndConversionType(
            Long userId,
            Long placeId,
            PlaceRecommendationConversionType conversionType
    );
}
