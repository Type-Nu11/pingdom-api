package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.domain.admin.exception.AdminErrorCode;
import com.typenull.pingdom.domain.admin.exception.AdminException;
import com.typenull.pingdom.engagement.domain.repository.PostReportRepository;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.repository.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPostServiceImpl implements AdminPostService {

    private final MapImageRepository mapImageRepository;
    private final PostReportRepository postReportRepository;
    private final S3ObjectStorage s3ObjectStorage;

    @Override
    @Transactional
    public void deletePost(Long postId) {
        MapImage mapImage = mapImageRepository.findById(postId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.POST_NOT_FOUND));

        // 게시글 삭제 전에 신고 연관을 먼저 끊어 FK 제약 위반을 방지한다.
        postReportRepository.detachMapImageByMapImageId(postId);

        String keyToDelete = resolveS3Key(mapImage);
        if (StringUtils.hasText(keyToDelete)) {
            deleteFromS3(keyToDelete);
        }

        mapImageRepository.delete(mapImage);
    }

    private void deleteFromS3(String s3Key) {
        try {
            s3ObjectStorage.delete(s3Key);
        } catch (S3StorageException exception) {
            if (exception.getError() == S3StorageError.NOT_CONFIGURED) {
                // 테스트/로컬 환경 등에서 S3 비활성화 상태일 수 있어, 강제 삭제는 DB 삭제를 우선
                log.warn("S3 is not configured. Skipping S3 delete. key={}", s3Key);
                return;
            }
            if (exception.getError() == S3StorageError.CONNECTION_ERROR) {
                throw new AdminException(AdminErrorCode.S3_CONNECTION_ERROR, exception);
            }
            throw new AdminException(AdminErrorCode.POST_DELETE_FAILED, exception);
        }
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
}
