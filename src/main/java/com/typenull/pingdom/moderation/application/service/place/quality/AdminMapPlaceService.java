package com.typenull.pingdom.moderation.application.service.place.quality;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import com.typenull.pingdom.verification.infrastructure.ScoutFieldReportRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMapPlaceService {
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceEventRepository placeEventRepository;
    private final LocationCheckInRepository locationCheckInRepository;
    private final ScoutFieldReportRepository scoutFieldReportRepository;
    private final MapImageRepository mapImageRepository;
    private final AdminPostService adminPostService;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public void deletePlace(long placeId, Long adminUserId) {
        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        if (placeEventRepository.existsByPlace_Id(placeId)) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_CONNECTED);
        }
        if (locationCheckInRepository.existsByPlaceId(placeId)) {
            throw new AdminException(AdminErrorCode.PLACE_CHECK_IN_CONNECTED);
        }
        if (scoutFieldReportRepository.existsByPlaceId(placeId)) {
            throw new AdminException(AdminErrorCode.PLACE_SCOUT_FIELD_REPORT_CONNECTED);
        }
        Map<String, Object> beforeState = AdminPlaceServiceSupport.placeState(mapPlace);
        List<Long> linkedPostIds = mapImageRepository.findIdsByMapPlaceId(placeId);

        linkedPostIds.forEach(postId -> adminPostService.deletePost(postId, adminUserId));

        mapPlaceRepository.delete(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_DELETED,
                AdminAuditTargetType.PLACE,
                placeId,
                "PLACE_DELETED",
                beforeState,
                Map.of("placeId", placeId, "deleted", true, "deletedPostCount", linkedPostIds.size())
        );
    }
}
