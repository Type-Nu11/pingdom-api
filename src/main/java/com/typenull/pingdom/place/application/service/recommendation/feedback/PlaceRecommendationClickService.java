package com.typenull.pingdom.place.application.service.recommendation.feedback;

import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationVersionSnapshotService;

import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
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

/**
 * 운영 중이며 공개된 장소의 추천 클릭과 전체·버전별 집계 증가를 같은 트랜잭션에서 기록합니다.
 * 로그인 사용자의 requestId 재사용은 사전 조회로 거절하며 이 조회만으로 동시 요청의 유일성이 보장되지는 않습니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationClickService {

    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;
    private final MapPlaceRepository mapPlaceRepository;

    /**
     * 운영 중·공개 장소의 클릭을 저장하고 전체 및 해당 버전 스냅샷의 클릭 수를 증가시킵니다.
     * 인증 사용자와 공백 아닌 요청 ID가 함께 주어지면 이미 사용한 요청을 거절하며, 이 중복 사전 조회가 동시 요청을 잠그지는 않습니다.
     */
    @Transactional
    public void recordClick(Long userId, Long placeId, String recommendationVersion, String requestId) {
        if (!mapPlaceRepository.existsByIdAndOperatingStatusAndDiscoveryStatus(
                placeId,
                PlaceOperatingStatus.OPERATING,
                PlaceDiscoveryStatus.VISIBLE
        )) {
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

    /**
     * 전달된 장소들의 누적 클릭 수를 일괄 집계해 불변 맵으로 반환합니다.
     * 빈 입력은 빈 맵이며 집계 결과가 없는 장소의 0 값은 추가하지 않습니다.
     */
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
