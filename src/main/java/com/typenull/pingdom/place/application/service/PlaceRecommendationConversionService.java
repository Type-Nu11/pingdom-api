package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationConversionRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationConversionService {

    private static final long CONVERSION_WINDOW_DAYS = 7L;

    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Transactional
    public void recordConversionIfEligible(
            Long userId,
            Long placeId,
            PlaceRecommendationConversionType conversionType
    ) {
        if (placeRecommendationConversionRepository.existsByUserIdAndPlaceIdAndConversionType(
                userId,
                placeId,
                conversionType
        )) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(CONVERSION_WINDOW_DAYS);
        PlaceRecommendationClick recentClick =
                placeRecommendationClickRepository
                        .findFirstByUserIdAndPlaceIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                                userId,
                                placeId,
                                cutoff
                        )
                        .orElse(null);

        if (recentClick == null) {
            return;
        }

        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(recentClick.getId())
                .placeId(placeId)
                .userId(userId)
                .conversionType(conversionType)
                .build());
    }
}
