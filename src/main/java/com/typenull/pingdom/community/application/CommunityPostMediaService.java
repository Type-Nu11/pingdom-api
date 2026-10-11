package com.typenull.pingdom.community.application;

import com.typenull.pingdom.community.api.dto.CommunityPostMediaUploadResponse;
import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostMedia;
import com.typenull.pingdom.community.domain.CommunityPostMediaStatus;
import com.typenull.pingdom.community.domain.exception.CommunityErrorCode;
import com.typenull.pingdom.community.domain.exception.CommunityException;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostMediaRepository;
import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 임시 사진의 소유권·연결·만료를 관리하고 S3 삭제를 기존 Outbox에 위임. */
@Service
@Slf4j
public class CommunityPostMediaService {
    private final CommunityPostMediaRepository mediaRepository;
    private final ImageUploadProcessor imageProcessor;
    private final S3ObjectStorage objectStorage;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;
    private final Clock clock;
    private final TransactionTemplate metadataTransaction;

    public CommunityPostMediaService(CommunityPostMediaRepository mediaRepository, ImageUploadProcessor imageProcessor,
                                    S3ObjectStorage objectStorage, S3ObjectDeleteOutboxPublisher deletePublisher,
                                    Clock clock, PlatformTransactionManager transactionManager) {
        this.mediaRepository = mediaRepository;
        this.imageProcessor = imageProcessor;
        this.objectStorage = objectStorage;
        this.deletePublisher = deletePublisher;
        this.clock = clock;
        metadataTransaction = new TransactionTemplate(transactionManager);
        metadataTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** S3 I/O는 DB 트랜잭션 밖에서 수행하고 메타데이터 커밋 실패까지 보상 삭제 대상으로 처리. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommunityPostMediaUploadResponse upload(long userId, MultipartFile file) {
        var processed = imageProcessor.process(file);
        S3ObjectStorage.S3PutResult uploaded;
        try {
            uploaded = objectStorage.put(processed.originalBytes(), processed.originalFilename(),
                    processed.contentType(), "community-posts");
        } catch (S3ObjectStorage.S3StorageException exception) {
            throw new CommunityException(CommunityErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }
        try {
            return metadataTransaction.execute(status -> CommunityPostMediaUploadResponse.from(
                    mediaRepository.saveAndFlush(CommunityPostMedia.upload(userId, uploaded.key(), uploaded.url(),
                            processed.contentType(), processed.originalBytes().length, LocalDateTime.now(clock)))));
        } catch (RuntimeException failure) {
            compensateUpload(uploaded.key(), failure);
            throw failure;
        }
    }

    @Transactional
    public void connect(long userId, CommunityPost post, List<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) { return; }
        if (imageIds.size() > 10 || imageIds.stream().anyMatch(java.util.Objects::isNull)
                || imageIds.size() != new HashSet<>(imageIds).size()) {
            throw new CommunityException(CommunityErrorCode.MEDIA_INVALID_REQUEST);
        }
        // 표시 순서가 다른 중복 등록도 동일한 ID 순서로 잠가 교착을 방지한다.
        Map<Long, CommunityPostMedia> mediaById = new HashMap<>();
        for (Long id : imageIds.stream().sorted().toList()) {
            mediaById.put(id, requireOwnedForUpdate(id, userId));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (Long id : imageIds) {
            CommunityPostMedia media = mediaById.get(id);
            if (media.getStatus() == CommunityPostMediaStatus.CONNECTED) {
                throw new CommunityException(CommunityErrorCode.MEDIA_ALREADY_CONNECTED);
            }
            if (media.isExpiredAt(now)) {
                throw new CommunityException(CommunityErrorCode.MEDIA_EXPIRED);
            }
        }
        for (int order = 0; order < imageIds.size(); order++) {
            mediaById.get(imageIds.get(order)).connect(post, order, now);
        }
    }

    @Transactional
    public void cancel(long userId, long imageId) {
        CommunityPostMedia media = requireOwnedForUpdate(imageId, userId);
        if (media.getStatus() == CommunityPostMediaStatus.CONNECTED) {
            throw new CommunityException(CommunityErrorCode.MEDIA_ALREADY_CONNECTED);
        }
        requestDeletion(media, "USER_CANCELLED");
        mediaRepository.delete(media);
    }

    @Transactional
    public int purgeExpiredUploads() {
        int deleted = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (int batch = 0; batch < 10; batch++) {
            List<CommunityPostMedia> expired = mediaRepository.findExpiredForUpdate(now);
            if (expired.isEmpty()) { break; }
            expired.forEach(media -> requestDeletion(media, "UPLOAD_EXPIRED"));
            mediaRepository.deleteAllInBatch(expired);
            deleted += expired.size();
        }
        return deleted;
    }

    private CommunityPostMedia requireOwnedForUpdate(long id, long userId) {
        CommunityPostMedia media = mediaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CommunityException(CommunityErrorCode.MEDIA_NOT_FOUND));
        if (!media.isOwnedBy(userId)) { throw new CommunityException(CommunityErrorCode.MEDIA_FORBIDDEN); }
        return media;
    }

    private void requestDeletion(CommunityPostMedia media, String reason) {
        deletePublisher.publish(media.getS3Key(), "COMMUNITY_POST_MEDIA", String.valueOf(media.getId()), reason);
    }

    private void compensateUpload(String key, RuntimeException originalFailure) {
        try {
            objectStorage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
            try {
                metadataTransaction.executeWithoutResult(status ->
                        deletePublisher.publish(key, "COMMUNITY_POST_MEDIA", "UNCOMMITTED", "UPLOAD_ROLLED_BACK"));
            } catch (RuntimeException outboxFailure) {
                originalFailure.addSuppressed(outboxFailure);
                log.error("게시글 사진 보상 삭제와 Outbox 등록이 실패했습니다. 고아 객체 정리가 필요합니다. key={}", key);
            }
        }
    }
}
