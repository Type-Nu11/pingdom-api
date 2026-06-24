package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
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

@Service
@RequiredArgsConstructor
public class AdminPostServiceImpl implements AdminPostService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final PlaceGrowthService placeGrowthService;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public void deletePost(Long postId, Long adminUserId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));
        Map<String, Object> beforeState = postState(mapImage, false, null);

        // 게시글 삭제 전에 신고 연관을 먼저 끊어 FK 제약 위반을 방지한다.
        postReportRepository.detachMapImageByMapImageId(postId);

        String keyToDelete = resolveS3Key(mapImage);

        MapPlace mapPlace = mapImage.getMapPlace();
        if (mapPlace != null) {
            placeGrowthService.decreasePhotoCount(mapPlace.getId());
        }
        mapImageRepository.delete(mapImage);
        publishS3Delete(keyToDelete, postId);
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

    @Override
    @Transactional
    public void hidePost(Long postId, String reason, Long adminUserId) {
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

    @Override
    @Transactional
    public void restorePost(Long postId, String reason, Long adminUserId) {
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

    // imageUrl이 S3 URL(virtual-hosted style / path style)일 때 key를 추출
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

            // bucket을 모르더라도 path에서 key를 얻을 수 있으면 그대로 사용 (S3ObjectStorage가 bucket 설정을 검증)
            return normalizedPath;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private void publishS3Delete(String s3Key, Long postId) {
        s3ObjectDeleteOutboxPublisher.publish(
                s3Key,
                "MAP_IMAGE",
                postId == null ? null : String.valueOf(postId),
                "ADMIN_MAP_IMAGE_DELETED"
        );
    }

    private Map<String, Object> postState(MapImage mapImage, boolean deleted, String s3KeyToDelete) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("postId", mapImage.getId());
        state.put("title", mapImage.getTitle());
        state.put("imageUrl", mapImage.getImageUrl());
        state.put("s3Key", mapImage.getS3Key());
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
