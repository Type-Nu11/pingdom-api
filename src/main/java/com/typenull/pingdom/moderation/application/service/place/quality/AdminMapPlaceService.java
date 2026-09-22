package com.typenull.pingdom.moderation.application.service.place.quality;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
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

/**
 * 장소를 잠근 뒤 연결 이벤트·체크인·Scout 제보가 있으면 삭제를 거절합니다.
 * 사진 삭제와 북마크·추세 이력 정리, 장소 삭제, 감사 기록은 같은 DB 트랜잭션에 참여하고 실제 S3 삭제는 outbox 작업에 맡깁니다.
 * 이 서비스가 사전 검사하지 않는 다른 FK 제약도 최종 삭제를 거절할 수 있습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMapPlaceService {
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceEventRepository placeEventRepository;
    private final LocationCheckInRepository locationCheckInRepository;
    private final ScoutFieldReportRepository scoutFieldReportRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;
    private final MapImageRepository mapImageRepository;
    private final AdminPostService adminPostService;
    private final AdminAuditLogService adminAuditLogService;

    /**
     * 장소 행을 잠그고 연결 이벤트·체크인·Scout 제보가 없을 때 연결 사진, 북마크 추세 이력, 북마크, 장소 순서로 삭제합니다.
     * 연결 사진 삭제에는 AdminPostService의 권한 검사와 S3 삭제 outbox 등록이 적용되며 DB 삭제와 감사 기록은 같은 트랜잭션에 참여합니다.
     * 장소 없음·보호하는 연관 데이터·남은 DB 제약은 삭제를 거절할 수 있고 반환 시 S3 정리까지 완료된 것은 아닙니다.
     */
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

        // 북마크 이력의 RESTRICT FK와 실제 사용자 즐겨찾기를 모두 정리한 뒤 장소를 삭제합니다.
        int deletedBookmarkTrendEventCount = mapBookmarkTrendEventRepository.deleteAllByPlaceId(placeId);
        int deletedBookmarkCount = mapBookmarkRepository.deleteAllByPlaceId(placeId);
        mapPlaceRepository.delete(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_DELETED,
                AdminAuditTargetType.PLACE,
                placeId,
                "PLACE_DELETED",
                beforeState,
                Map.of(
                        "placeId", placeId,
                        "deleted", true,
                        "deletedPostCount", linkedPostIds.size(),
                        "deletedBookmarkCount", deletedBookmarkCount,
                        "deletedBookmarkTrendEventCount", deletedBookmarkTrendEventCount
                )
        );
    }
}
