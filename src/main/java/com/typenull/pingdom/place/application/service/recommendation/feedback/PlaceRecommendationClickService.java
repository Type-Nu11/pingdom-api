package com.typenull.pingdom.place.application.service.recommendation.feedback;

import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationVersionSnapshotService;

import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationClickService {

    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;
    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public void recordClick(Long userId, Long placeId, String recommendationVersion, String requestId) {
        if (!mapPlaceRepository.existsByIdAndOperatingStatus(placeId, PlaceOperatingStatus.OPERATING)) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        if (StringUtils.hasText(requestId)
                && userId != null
                && placeRecommendationClickRepository.existsByUserIdAndRequestId(userId, requestId)) {
            log.warn("recommendation click requestId reused. userId={}, placeId={}, requestId={}", userId, placeId, requestId);
            throw new RateLimitException("이미 사용한 추천 요청입니다. 새로 조회한 추천 결과로 다시 시도해주세요.");
        }

        placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(placeId)
                .userId(userId)
                .recommendationVersion(recommendationVersion)
                .requestId(requestId)
                .build());

        placeRecommendationSnapshotService.increaseClickCounts(java.util.List.of(placeId));
        placeRecommendationVersionSnapshotService.increaseClickCounts(
                java.util.List.of(placeId),
                recommendationVersion
        );
    }

    public Map<Long, Long> loadClickCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> clickCounts = new HashMap<>();
        for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                placeRecommendationClickRepository.countClicksByPlaceIds(placeIds)) {
            clickCounts.put(projection.getPlaceId(), projection.getClickCount());
        }
        return Map.copyOf(clickCounts);
    }

    public long countAllClicks() {
        return placeRecommendationClickRepository.count();
    }
}
