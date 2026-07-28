package com.typenull.pingdom.moderation.application.service.place.merge;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeRestoreResponse;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminMapPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 장소 병합·이력 조회·복구 유스케이스의 진입점이다. */
@Service
@RequiredArgsConstructor
public class AdminPlaceMergeService {
    private final AdminMapPlaceService adminMapPlaceService;

    public AdminMapPlaceMergeResponse mergePlaces(Long adminUserId, AdminMapPlaceMergeRequest request) {
        return adminMapPlaceService.mergePlaces(adminUserId, request);
    }

    public AdminPlaceMergeHistoryResponse listMergeHistories() {
        return adminMapPlaceService.listMergeHistories();
    }

    public AdminPlaceMergeRestoreResponse restoreMerge(Long adminUserId, Long historyId) {
        return adminMapPlaceService.restoreMerge(adminUserId, historyId);
    }
}
