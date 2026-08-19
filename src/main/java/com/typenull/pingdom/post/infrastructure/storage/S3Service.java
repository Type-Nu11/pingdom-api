package com.typenull.pingdom.post.infrastructure.storage;

import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;
import com.typenull.pingdom.post.infrastructure.storage.image.ProcessedImageUpload;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.statistics.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.place.PlaceMediaService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.api.dto.image.PostUpdateResponse;
import com.typenull.pingdom.post.api.dto.image.PostUploadRequest;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
/** 게시글 이미지의 업로드·교체·삭제와 S3 객체 URL 변환을 조정합니다. */
public class S3Service {

    private final S3ObjectStorage s3ObjectStorage;
    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceMediaService placeMediaService;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;
    private final ImageUploadProcessor imageUploadProcessor;

    public PostResponse uploadImage(PostUploadRequest request, long userId) {
        Long placeId = resolvePlaceId(request, userId);

        if(mapImageRepository.existsByUserIdAndMapPlace_Id(userId, placeId)){
            throw new MapException(MapErrorCode.ALREADY_POSTED);
        }

        String username = userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        StoredImageObjects storedImageObjects = uploadProcessedImage(request.file());

        try {
            return savePost(request, userId, username, storedImageObjects, placeId);
        } catch (MapException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        } catch (Exception e) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        }
    }

    private Long resolvePlaceId(PostUploadRequest request, long userId) {
        String kakaoPlaceId = normalizeKakaoPlaceId(request.kakaoPlaceId());
        if (kakaoPlaceId != null) {
            return mapPlaceRepository.findByKakaoPlaceId(kakaoPlaceId)
                    .map(MapPlace::getId)
                    .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        }

        Long placeId = request.placeId();
        if (placeId == null) {
            throw new MapException(MapErrorCode.PLACE_REGISTRATION_APPROVAL_REQUIRED);
        }

        return mapPlaceRepository.findById(placeId)
                .map(MapPlace::getId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private String normalizeKakaoPlaceId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public PostUpdateResponse updateImage(PostUpdateRequest request, Long userId, Long imageId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        if (!Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_UPDATE);
        }

        String oldS3Key = mapImage.getS3Key();
        String oldThumbnailS3Key = mapImage.getThumbnailS3Key();

        StoredImageObjects storedImageObjects = uploadProcessedImage(request.file());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        PostUpdateResponse response = transactionTemplate.execute(status -> {
            registerRollbackCleanup(storedImageObjects.keys());

            MapImage imageToUpdate = mapImageRepository.findWithMapPlaceById(imageId)
                    .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

            imageToUpdate.update(
                    request.title(),
                    request.description(),
                    storedImageObjects.original().url(),
                    storedImageObjects.original().key(),
                    storedImageObjects.thumbnail().url(),
                    storedImageObjects.thumbnail().key()
            );

            mapImageRepository.save(imageToUpdate);
            publishS3Delete(oldS3Key, imageToUpdate.getId(), "MAP_IMAGE_REPLACED");
            publishS3Delete(oldThumbnailS3Key, imageToUpdate.getId(), "MAP_IMAGE_THUMBNAIL_REPLACED");

            return new PostUpdateResponse(imageToUpdate.getId(), "게시글을 수정했습니다.");
        });

        return response;
    }

    public PostResponse deleteImage(Long imageId, Long userId) {
        // 지우려는 이미지가 있는지
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        // 본인이 맞는지
        if (!Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_DELETED);
        }

        String s3Key = mapImage.getS3Key();
        PlaceGrowthSnapshot placeGrowth = deletePostRecord(mapImage, s3Key);

        Long placeId = mapImage.getMapPlace() != null ? mapImage.getMapPlace().getId() : null;
        return new PostResponse(imageId, imageId, placeId, "게시글을 삭제했습니다", placeGrowth);
    }
    private PostResponse savePost(
            PostUploadRequest request,
            long userId,
            String username,
            StoredImageObjects storedImageObjects,
            Long placeId
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            registerRollbackCleanup(storedImageObjects.keys());

            MapPlace mapPlace = placeGrowthService.getPlaceForUpdate(placeId);
            MapImage mapImage = MapImage.builder()
                    .imageUrl(storedImageObjects.original().url())
                    .s3Key(storedImageObjects.original().key())
                    .thumbnailUrl(storedImageObjects.thumbnail().url())
                    .thumbnailS3Key(storedImageObjects.thumbnail().key())
                    .title(request.title())
                    .description(request.description())
                    .userId(userId)
                    .username(username)
                    .mapPlace(mapPlace)
                    .build();

            MapImage saved = mapImageRepository.save(mapImage);
            placeMediaService.recordVerificationMedia(saved);
            PlaceGrowthSnapshot placeGrowth = placeGrowthService.increasePhotoCount(mapPlace);
            placeRecommendationSnapshotService.refresh(mapPlace.getId());
            return new PostResponse(saved.getId(), saved.getId(), mapPlace.getId(), "게시글을 저장했습니다.", placeGrowth);
        });
    }

    private PlaceGrowthSnapshot deletePostRecord(MapImage mapImage, String s3Key) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            PlaceGrowthSnapshot placeGrowth = null;
            MapPlace mapPlace = mapImage.getMapPlace();
            if (mapPlace != null) {
                placeGrowth = placeGrowthService.decreasePhotoCount(mapPlace.getId());
            }
            postReportRepository.detachMapImageByMapImageId(mapImage.getId());
            mapImageRepository.delete(mapImage);
            if (mapPlace != null) {
                placeRecommendationSnapshotService.refresh(mapPlace.getId());
            }
            publishS3Delete(s3Key, mapImage.getId(), "MAP_IMAGE_DELETED");
            publishS3Delete(mapImage.getThumbnailS3Key(), mapImage.getId(), "MAP_IMAGE_THUMBNAIL_DELETED");
            return placeGrowth;
        });
    }

    private StoredImageObjects uploadProcessedImage(org.springframework.web.multipart.MultipartFile file) {
        ProcessedImageUpload processedImage = imageUploadProcessor.process(file);
        S3ObjectStorage.S3PutResult original = null;
        try {
            original = s3ObjectStorage.put(
                    processedImage.originalBytes(),
                    processedImage.originalFilename(),
                    processedImage.contentType(),
                    "map"
            );
            S3ObjectStorage.S3PutResult thumbnail = s3ObjectStorage.put(
                    processedImage.thumbnailBytes(),
                    processedImage.thumbnailFilename(),
                    processedImage.thumbnailContentType(),
                    "map/thumbnails"
            );
            return new StoredImageObjects(original, thumbnail);
        } catch (S3StorageException exception) {
            cleanupUploadedObject(original);
            throw toMapException(exception);
        } catch (RuntimeException exception) {
            cleanupUploadedObject(original);
            throw exception;
        }
    }

    private void cleanupUploadedObject(S3ObjectStorage.S3PutResult putResult) {
        if (putResult == null || !StringUtils.hasText(putResult.key())) {
            return;
        }
        try {
            s3ObjectStorage.delete(putResult.key());
        } catch (RuntimeException exception) {
            log.warn("이미지 업로드 실패 후 선행 S3 객체 정리에 실패했습니다. key={}", putResult.key(), exception);
        }
    }

    private void registerRollbackCleanup(List<String> uploadedS3Keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        List<String> cleanupKeys = uploadedS3Keys.stream()
                .filter(StringUtils::hasText)
                .toList();
        if (cleanupKeys.isEmpty()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                for (String uploadedS3Key : cleanupKeys) {
                    try {
                        s3ObjectStorage.delete(uploadedS3Key);
                    } catch (RuntimeException exception) {
                        log.warn("게시글 업로드 롤백 후 S3 정리에 실패했습니다. key={}", uploadedS3Key, exception);
                    }
                }
            }
        });
    }

    private MapException toMapException(S3StorageException exception) {
        S3StorageError error = exception.getError();
        if (error == S3StorageError.NOT_CONFIGURED) {
            return new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }
        if (error == S3StorageError.CONNECTION_ERROR) {
            return new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
        return new MapException(MapErrorCode.UPLOAD_ERROR);
    }

    private void publishS3Delete(String s3Key, Long mapImageId, String reason) {
        if (!StringUtils.hasText(s3Key)) {
            return;
        }
        s3ObjectDeleteOutboxPublisher.publish(
                s3Key,
                "MAP_IMAGE",
                mapImageId == null ? null : String.valueOf(mapImageId),
                reason
        );
    }

    private record StoredImageObjects(
            S3ObjectStorage.S3PutResult original,
            S3ObjectStorage.S3PutResult thumbnail
    ) {
        private List<String> keys() {
            return Arrays.asList(original.key(), thumbnail.key());
        }
    }

}
