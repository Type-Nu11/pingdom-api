package com.typenull.pingdom.moderation.application.service.place.recommendation;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateResponse;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 추천 트래픽 정책과 추천 스냅샷 관리 유스케이스의 진입점이다. */
@Service
@RequiredArgsConstructor
public class AdminPlaceRecommendationPolicyService {
    private final AdminMapPlaceService adminMapPlaceService;
    private final PlaceRecommendationSnapshotResyncService placeRecommendationSnapshotResyncService;

    public AdminPlaceRecommendationTrafficUpdateResponse updateRecommendationTraffic(
            Long adminUserId, AdminPlaceRecommendationTrafficUpdateRequest request
    ) {
        return adminMapPlaceService.updateRecommendationTraffic(adminUserId, request);
    }

    public PlaceRecommendationSnapshotResyncService.SnapshotResyncResult resyncRecommendationSnapshots() {
        return placeRecommendationSnapshotResyncService.resyncAll();
    }
}
