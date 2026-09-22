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

/**
 * 리뷰 사진을 24시간 임시 업로드로 보관하고 작성자·장소·만료를 확인해 리뷰에 연결.
 * 취소·만료 사진은 DB에서 제거하고 S3 삭제는 outbox에 맡기며 연결 사진은 이 임시 정리 대상에서 제외.
 */
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

    /**
     * 이미지 처리 후 S3에 올리고 임시 업로드 행을 저장.
     * 메서드 안에서 관찰한 저장 실패에는 S3 삭제를 시도하지만 지연 flush·최종 커밋 실패는 보상 범위에서 제외.
     */
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

    /**
     * 최대 3개의 중복 없는 업로드 ID를 요청 순서대로 잠가 본인·같은 장소·미연결·미만료 여부를 확인한 뒤 리뷰에 연결.
     * null·빈 목록은 건너뛰고 표시 순서는 0부터 부여. 하나라도 거절되면 호출 트랜잭션의 연결 변경 전체가 실패.
     */
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

    /**
     * 업로드 행을 잠가 본인과 장소가 일치하는 미연결 사진인지 확인한 뒤 메타데이터를 삭제.
     * S3 삭제는 outbox에 요청하며 이미 리뷰에 연결된 사진은 취소를 거절.
     */
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

    /**
     * 만료된 UPLOADED 사진을 100개씩 최대 10묶음 처리.
     * 전체 반복이 하나의 트랜잭션이므로 중간 실패 시 이 호출의 DB 삭제와 outbox 기록도 함께 롤백됨.
     */
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
