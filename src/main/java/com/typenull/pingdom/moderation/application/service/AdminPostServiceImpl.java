package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.storage.s3.outbox.S3ObjectDeleteOutboxPublisher;
import java.net.URI;
import java.net.URISyntaxException;
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

    @Override
    @Transactional
    public void deletePost(Long postId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));

        // 게시글 삭제 전에 신고 연관을 먼저 끊어 FK 제약 위반을 방지한다.
        postReportRepository.detachMapImageByMapImageId(postId);

        String keyToDelete = resolveS3Key(mapImage);

        MapPlace mapPlace = mapImage.getMapPlace();
        if (mapPlace != null) {
            placeGrowthService.decreasePhotoCount(mapPlace.getId());
        }
        mapImageRepository.delete(mapImage);
        publishS3Delete(keyToDelete, postId);
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
}
