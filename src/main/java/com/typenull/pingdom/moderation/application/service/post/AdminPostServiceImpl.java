package com.typenull.pingdom.moderation.application.service.post;

import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;

import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * REPORT_REVIEW 권한을 확인하고 사진 게시글의 삭제·숨김·복구와 장소 사진 집계를 변경.
 * DB 상태·감사 기록·객체 삭제 outbox를 함께 처리하며, 숨김·복구가 실제 전이된 경우에만 집계를 증감.
 */
@Service
@RequiredArgsConstructor
public class AdminPostServiceImpl implements AdminPostService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final PlaceGrowthService placeGrowthService;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminRoleAuthorizationService authorizationService;
    private final Clock clock;

    /**
     * REPORT_REVIEW 권한과 게시글 존재를 확인한 뒤 신고 연결을 끊고 게시글 영구 삭제.
     * 연결된 장소의 사진 수는 게시글의 현재 숨김 여부와 무관하게 감소.
     * DB 삭제·사진 집계·감사 기록·확보된 원본/썸네일 키의 삭제 outbox 저장은 같은 트랜잭션에 참여.
     * 응답 범위는 DB 반영까지이며 실제 S3 삭제는 커밋 후 worker에서 비동기 처리.
     */
    @Override
    @Transactional
    public void deletePost(Long postId, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));
        Map<String, Object> beforeState = postState(mapImage, false, null);

        // 게시글 삭제 전에 신고 연관을 먼저 끊어 FK 제약 위반을 방지.
        postReportRepository.detachMapImageByMapImageId(postId);

        String keyToDelete = resolveS3Key(mapImage);
        String thumbnailKeyToDelete = mapImage.getThumbnailS3Key();

        MapPlace mapPlace = mapImage.getMapPlace();
        if (mapPlace != null) {
            placeGrowthService.decreasePhotoCount(mapPlace.getId());
        }
        mapImageRepository.delete(mapImage);
        publishS3Delete(keyToDelete, postId, "ADMIN_MAP_IMAGE_DELETED");
        publishS3Delete(thumbnailKeyToDelete, postId, "ADMIN_MAP_IMAGE_THUMBNAIL_DELETED");
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.POST_DELETED,
                AdminAuditTargetType.POST,
                postId,
                "ADMIN_MAP_IMAGE_DELETED",
                beforeState,
                postState(mapImage, true, keyToDelete)
        );
    }

    /**
     * REPORT_REVIEW 권한과 게시글 존재를 확인하고 공개 중인 게시글만 AUTO_HIDDEN으로 전환.
     * 실제 전이 시 사유·시각·관리자를 기록하고 연결 장소의 사진 수를 감소시키며 S3 객체는 유지.
     * 이미 숨겨진 게시글은 상태·집계를 바꾸지 않아도 감사 기록을 추가하며 모든 DB 변경은 같은 트랜잭션에 참여.
     */
    @Override
    @Transactional
    public void hidePost(Long postId, String reason, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));
        Map<String, Object> beforeState = postState(mapImage, false, null);

        boolean hidden = mapImage.autoHide(reason, LocalDateTime.now(clock), adminUserId);
        if (hidden && mapImage.getMapPlace() != null) {
            placeGrowthService.decreasePhotoCount(mapImage.getMapPlace().getId());
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.POST_HIDDEN,
                AdminAuditTargetType.POST,
                postId,
                reason,
                beforeState,
                postState(mapImage, false, null)
        );
    }

    /**
     * REPORT_REVIEW 권한과 게시글 존재를 확인하고 비공개 상태를 ACTIVE로 전환. 영구 삭제된 게시글은 복구 불가.
     * 실제 전이 시 복구 사유·시각·관리자를 기록하고 연결 장소의 사진 수 증가. S3 작업은 처리 범위에서 제외.
     * 이미 공개된 게시글의 상태·집계는 유지하되 감사 기록을 추가하며 모든 DB 변경은 같은 트랜잭션에 참여.
     */
    @Override
    @Transactional
    public void restorePost(Long postId, String reason, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.REPORT_REVIEW);
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));
        Map<String, Object> beforeState = postState(mapImage, false, null);

        boolean restored = mapImage.restore(reason, LocalDateTime.now(clock), adminUserId);
        if (restored && mapImage.getMapPlace() != null) {
            placeGrowthService.increasePhotoCount(mapImage.getMapPlace().getId());
        }
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.POST_RESTORED,
                AdminAuditTargetType.POST,
                postId,
                reason,
                beforeState,
                postState(mapImage, false, null)
        );
    }

    private String resolveS3Key(MapImage mapImage) {
        if (StringUtils.hasText(mapImage.getS3Key())) {
            return mapImage.getS3Key();
        }
        return extractKeyFromUrlIfPossible(mapImage.getImageUrl());
    }

    // 저장 키가 없는 과거 데이터는 URL의 path를 후보 키로 사용. 호스트의 실제 S3 여부는 검사 범위에서 제외.
    private String extractKeyFromUrlIfPossible(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        try {
            URI uri = new URI(imageUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null) {
                return null;
            }

            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
            if (!StringUtils.hasText(normalizedPath)) {
                return null;
            }

            // 선행 슬래시만 제거하므로 path-style URL의 bucket 부분도 여기에 포함될 수 있음.
            return normalizedPath;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private void publishS3Delete(String s3Key, Long postId, String reason) {
        if (!StringUtils.hasText(s3Key)) {
            return;
        }
        s3ObjectDeleteOutboxPublisher.publish(
                s3Key,
                "MAP_IMAGE",
                postId == null ? null : String.valueOf(postId),
                reason
        );
    }

    private Map<String, Object> postState(MapImage mapImage, boolean deleted, String s3KeyToDelete) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("postId", mapImage.getId());
        state.put("title", mapImage.getTitle());
        state.put("imageUrl", mapImage.getImageUrl());
        state.put("s3Key", mapImage.getS3Key());
        state.put("thumbnailUrl", mapImage.getThumbnailUrl());
        state.put("thumbnailS3Key", mapImage.getThumbnailS3Key());
        state.put("s3KeyToDelete", s3KeyToDelete);
        state.put("userId", mapImage.getUserId());
        state.put("username", mapImage.getUsername());
        state.put("placeId", mapImage.getMapPlace() == null ? null : mapImage.getMapPlace().getId());
        state.put("visibilityStatus", mapImage.getVisibilityStatus());
        state.put("hiddenAt", mapImage.getHiddenAt());
        state.put("hiddenReason", mapImage.getHiddenReason());
        state.put("restoredAt", mapImage.getRestoredAt());
        state.put("restoredReason", mapImage.getRestoredReason());
        state.put("deleted", deleted);
        return state;
    }
}
