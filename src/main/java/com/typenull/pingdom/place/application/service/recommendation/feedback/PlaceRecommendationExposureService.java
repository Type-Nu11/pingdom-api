package com.typenull.pingdom.place.application.service.recommendation.feedback;

import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationVersionSnapshotService;

import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationExposure;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 결과의 입력 순서를 1부터 순위로 저장하고 전체·버전별 노출 집계를 증가시킵니다.
 * 기록은 프록시 호출 시 REQUIRES_NEW 트랜잭션에서 수행되며 requestId 재처리 중복 제거는 하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationExposureService {

    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    /**
     * 대상 장소들의 누적 노출 수를 일괄 집계해 불변 맵으로 반환합니다. 빈 입력은 빈 맵이며 조회되지 않은 장소의 0 값은 채우지 않습니다.
     */
    public Map<Long, Long> loadExposureCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> exposureCounts = new HashMap<>();
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresByPlaceIds(placeIds)) {
            exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
        }
        return Map.copyOf(exposureCounts);
    }

    public long countAllExposures() {
        return placeRecommendationExposureRepository.count();
    }

    /**
     * 표시된 장소 순서를 1부터의 순위로 기록하고 전체·버전별 노출 스냅샷을 함께 증가시킵니다. 빈 목록은 건너뜁니다.
     * 프록시를 통한 호출은 REQUIRES_NEW 트랜잭션을 사용하며 요청별 중복 검사는 없으므로 동일 목록의 재호출도 다시 기록됩니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordExposures(
            Long userId,
            double latitude,
            double longitude,
            String requestId,
            List<Long> placeIds,
            String recommendationVersion
    ) {
        if (placeIds.isEmpty()) {
            return;
        }

        List<PlaceRecommendationExposure> exposures = new ArrayList<>(placeIds.size());
        int ranking = 1;

        for (Long placeId : placeIds) {
            exposures.add(PlaceRecommendationExposure.builder()
                    .placeId(placeId)
                    .userId(userId)
                    .requestLatitude(latitude)
                    .requestLongitude(longitude)
                    .ranking(ranking++)
                    .recommendationVersion(recommendationVersion)
                    .requestId(requestId)
                    .build());
        }

        placeRecommendationExposureRepository.saveAll(exposures);
        placeRecommendationSnapshotService.increaseExposureCounts(placeIds);
        placeRecommendationVersionSnapshotService.increaseExposureCounts(placeIds, recommendationVersion);
    }
}
