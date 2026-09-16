package com.typenull.pingdom.place.application.service.review;

import com.typenull.pingdom.place.api.dto.review.PlaceReviewMediaUploadResponse;
import com.typenull.pingdom.place.domain.review.PlaceReview;
import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUpload;
import com.typenull.pingdom.place.domain.review.PlaceReviewMediaUploadStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceReviewMediaUploadRepository;
import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;
import com.typenull.pingdom.post.infrastructure.storage.image.ProcessedImageUpload;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceReviewMediaService {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final int MAX_CLEANUP_BATCHES = 10;
    private static final String S3_PREFIX = "place-reviews";

    private final MapPlaceRepository placeRepository;
    private final PlaceReviewMediaUploadRepository mediaRepository;
    private final ImageUploadProcessor imageUploadProcessor;
    private final S3ObjectStorage objectStorage;
    private final S3ObjectDeleteOutboxPublisher deletePublisher;
    private final Clock clock;

    @Transactional
    public PlaceReviewMediaUploadResponse upload(Long userId, Long placeId, MultipartFile file) {
        if (!placeRepository.existsById(placeId)) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        validateUploadRequest(file);

        var processed = imageUploadProcessor.process(file);
        S3ObjectStorage.S3PutResult uploaded = uploadToStorage(processed);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            PlaceReviewMediaUpload media = mediaRepository.save(PlaceReviewMediaUpload.upload(
                    placeId,
                    userId,
                    uploaded.key(),
                    uploaded.url(),
                    processed.contentType(),
                    processed.originalBytes().length,
                    now.plusHours(24),
                    now
            ));
            return PlaceReviewMediaUploadResponse.from(media);
        } catch (RuntimeException exception) {
            cleanupUploadedObject(uploaded.key());
            throw exception;
        }
    }

    @Transactional
    public void connect(Long userId, Long placeId, PlaceReview review, List<Long> reviewMediaIds) {
        if (reviewMediaIds == null || reviewMediaIds.isEmpty()) {
            return;
        }
        if (reviewMediaIds.size() > 3 || reviewMediaIds.size() != new HashSet<>(reviewMediaIds).size()) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_INVALID_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        for (int displayOrder = 0; displayOrder < reviewMediaIds.size(); displayOrder++) {
            PlaceReviewMediaUpload media = mediaRepository.findByIdForUpdate(reviewMediaIds.get(displayOrder))
                    .orElseThrow(() -> new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_NOT_FOUND));
            if (!media.isOwnedBy(userId, placeId)) {
                throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_FORBIDDEN);
            }
            if (media.getStatus() == PlaceReviewMediaUploadStatus.CONNECTED) {
                throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_ALREADY_CONNECTED);
            }
            if (media.isExpiredAt(now)) {
                throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_EXPIRED);
            }
            media.connect(review, displayOrder, now);
            review.addMediaUpload(media);
        }
    }

    @Transactional
    public void cancel(Long userId, Long placeId, Long reviewMediaId) {
        PlaceReviewMediaUpload media = mediaRepository.findByIdForUpdate(reviewMediaId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_NOT_FOUND));
        if (!media.isOwnedBy(userId, placeId)) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_FORBIDDEN);
        }
        if (media.getStatus() == PlaceReviewMediaUploadStatus.CONNECTED) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_ALREADY_CONNECTED);
        }
        deletePublisher.publish(media.getS3Key(), "PLACE_REVIEW_MEDIA", String.valueOf(media.getId()), "USER_CANCELLED");
        mediaRepository.delete(media);
    }

    @Transactional
    public int purgeExpiredUploads() {
        int deletedCount = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (int batch = 0; batch < MAX_CLEANUP_BATCHES; batch++) {
            List<PlaceReviewMediaUpload> expired = mediaRepository
                    .findAllByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                            PlaceReviewMediaUploadStatus.UPLOADED, now, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (expired.isEmpty()) {
                break;
            }
            for (PlaceReviewMediaUpload media : expired) {
                deletePublisher.publish(media.getS3Key(), "PLACE_REVIEW_MEDIA", String.valueOf(media.getId()), "UPLOAD_EXPIRED");
            }
            mediaRepository.deleteAllInBatch(expired);
            deletedCount += expired.size();
            if (expired.size() < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        if (deletedCount > 0) {
            log.info("만료된 장소 리뷰 사진 삭제를 요청했습니다. deletedCount={}", deletedCount);
        }
        return deletedCount;
    }

    private void validateUploadRequest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_INVALID_REQUEST);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_FILE_TOO_LARGE);
        }
        if (!"image/jpeg".equalsIgnoreCase(file.getContentType()) && !"image/png".equalsIgnoreCase(file.getContentType())) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_UNSUPPORTED_TYPE);
        }
    }

    private S3ObjectStorage.S3PutResult uploadToStorage(ProcessedImageUpload processed) {
        try {
            return objectStorage.put(
                    processed.originalBytes(),
                    processed.originalFilename(),
                    processed.contentType(),
                    S3_PREFIX
            );
        } catch (S3StorageException exception) {
            throw new MapException(MapErrorCode.PLACE_REVIEW_MEDIA_STORAGE_UNAVAILABLE);
        }
    }

    private void cleanupUploadedObject(String key) {
        try {
            objectStorage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("리뷰 사진 DB 저장 실패 후 S3 객체 정리에 실패했습니다. key={}", key, exception);
        }
    }
}
