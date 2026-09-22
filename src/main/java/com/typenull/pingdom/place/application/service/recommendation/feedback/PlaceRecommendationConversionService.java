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

/**
 * 최근 7일 이내의 동일 사용자·장소 클릭이 있는 북마크/좋아요를 추천 전환으로 기록.
 * 가장 최근 클릭의 추천 버전과 선택적 특성 로그에 귀속하며 사용자·장소·전환 유형별로 최초 1건만 집계.
 * 사전 중복 조회 후 경쟁하는 삽입은 DB 유일 제약으로 실패할 수 있으며 해당 예외는 호출자에게 전파.
 */
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

    /**
     * 사용자·장소·전환 유형의 첫 기록에 한해 최근 7일 내 가장 최근 클릭으로 전환을 귀속.
     * 기존 전환이나 적격 클릭이 없으면 건너뛰고, 있으면 선택적 특성 로그 ID와 클릭 버전을 저장한 뒤 전체·버전 스냅샷을 증가시킴.
     * 중복 검사와 클릭 조회는 일반 조회이므로 동시 기록 경쟁의 직렬화는 보장 범위에서 제외.
     */
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
