package com.typenull.pingdom.place.application.service.recommendation.feedback;

import com.typenull.pingdom.place.application.service.recommendation.feature.PlaceRecommendationFeatureLogService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationVersionSnapshotService;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import java.time.Clock;
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
    private final PlaceRecommendationFeatureLogService placeRecommendationFeatureLogService;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;
    private final Clock clock;

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

        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(CONVERSION_WINDOW_DAYS);
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

        Long featureLogId = placeRecommendationFeatureLogService.findFeatureLogId(
                recentClick.getRequestId(),
                userId,
                placeId,
                recentClick.getRecommendationVersion()
        );

        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(recentClick.getId())
                .placeRecommendationFeatureLogId(featureLogId)
                .placeId(placeId)
                .userId(userId)
                .conversionType(conversionType)
                .recommendationVersion(recentClick.getRecommendationVersion())
                .build());
        placeRecommendationSnapshotService.increaseConversionCount(placeId, conversionType);
        placeRecommendationVersionSnapshotService.increaseConversionCount(
                placeId,
                recentClick.getRecommendationVersion(),
                conversionType
        );
    }
}
