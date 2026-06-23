package com.typenull.pingdom.post.application.service;

import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.dto.place.PlaceCreateResponse;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.support.PlaceCoordinateTokenStore.Entry;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.api.dto.image.PostUpdateResponse;
import com.typenull.pingdom.post.api.dto.image.PostUploadRequest;
import com.typenull.pingdom.post.application.port.PostImageStorage;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageStorageError;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageStorageException;
import com.typenull.pingdom.post.application.port.PostImageStorage.PostImageUploadResult;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.storage.s3.outbox.S3ObjectDeleteOutboxPublisher;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostCommandService {

    private final PostImageStorage postImageStorage;
    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final MapPlaceService mapPlaceService;
    private final PlatformTransactionManager transactionManager;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;

    public PostResponse uploadPost(PostUploadRequest request, long userId) {
        Long placeId = resolvePlaceId(request, userId);

        if (mapImageRepository.existsByUserIdAndMapPlace_Id(userId, placeId)) {
            throw new MapException(MapErrorCode.ALREADY_POSTED);
        }

        String username = userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        PostImageUploadResult uploadResult;
        try {
            uploadResult = postImageStorage.upload(request.file());
        } catch (PostImageStorageException exception) {
            throw toMapException(exception);
        }

        try {
            return savePost(request, userId, username, uploadResult, placeId);
        } catch (MapException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        }
    }

    public PostUpdateResponse updatePost(PostUpdateRequest request, Long userId, Long imageId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        if (!Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_UPDATE);
        }

        String oldS3Key = mapImage.getS3Key();
        PostImageUploadResult uploadResult;
        try {
            uploadResult = postImageStorage.upload(request.file());
        } catch (PostImageStorageException exception) {
            throw toMapException(exception);
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        return transactionTemplate.execute(status -> {
            registerRollbackCleanup(uploadResult.key());

            MapImage imageToUpdate = mapImageRepository.findWithMapPlaceById(imageId)
                    .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

            imageToUpdate.update(
                    request.title(),
                    request.description(),
                    uploadResult.url(),
                    uploadResult.key()
            );

            mapImageRepository.save(imageToUpdate);
            publishS3Delete(oldS3Key, imageToUpdate.getId(), "MAP_IMAGE_REPLACED");

            return new PostUpdateResponse(imageToUpdate.getId(), "게시글을 수정했습니다.");
        });
    }

    public PostResponse deletePost(Long imageId, Long userId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            MapImage mapImage = mapImageRepository.findWithMapPlaceById(imageId)
                    .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

            if (!Objects.equals(mapImage.getUserId(), userId)) {
                throw new MapException(MapErrorCode.OTHERS_NOT_DELETED);
            }

            String s3Key = mapImage.getS3Key();
            PlaceGrowthSnapshot placeGrowth = null;
            MapPlace mapPlace = mapImage.getMapPlace();
            Long placeId = null;
            if (mapPlace != null) {
                placeId = mapPlace.getId();
                placeGrowth = placeGrowthService.decreasePhotoCount(placeId);
            }
            postReportRepository.detachMapImageByMapImageId(mapImage.getId());
            mapImageRepository.delete(mapImage);
            if (placeId != null) {
                placeRecommendationSnapshotService.refresh(placeId);
            }
            publishS3Delete(s3Key, mapImage.getId(), "MAP_IMAGE_DELETED");
            return new PostResponse(imageId, imageId, placeId, "게시글을 삭제했습니다", placeGrowth);
        });
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
            return resolveOrCreatePlaceIdByCoordinateToken(request, userId);
        }

        return mapPlaceRepository.findById(placeId)
                .map(MapPlace::getId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private Long resolveOrCreatePlaceIdByCoordinateToken(PostUploadRequest request, long userId) {
        String normalizedPlaceName = trimToNull(request.placeName());
        String normalizedAddress = trimToNull(request.address());
        String normalizedCategory = trimToNull(request.category());

        if (normalizedPlaceName == null
                || normalizedAddress == null
                || normalizedCategory == null
                || !StringUtils.hasText(request.coordinateToken())) {
            throw new MapException(MapErrorCode.PLACE_ID_REQUIRED);
        }

        Entry coordinateTokenEntry = previewCoordinateToken(request.coordinateToken(), userId);
        return mapPlaceRepository.findFirstByNameAndAddressAndLatitudeAndLongitude(
                        normalizedPlaceName,
                        normalizedAddress,
                        coordinateTokenEntry.latitude(),
                        coordinateTokenEntry.longitude()
                )
                .map(MapPlace::getId)
                .orElseGet(() -> createPlaceByCoordinateToken(
                        request.coordinateToken(),
                        normalizedPlaceName,
                        normalizedAddress,
                        normalizedCategory,
                        userId
                ));
    }

    private Long createPlaceByCoordinateToken(
            String coordinateToken,
            String placeName,
            String address,
            String category,
            long userId
    ) {
        PlaceCreateResponse placeResponse = mapPlaceService.uploadPlaceByToken(
                null,
                placeName,
                address,
                category,
                null,
                coordinateToken,
                userId
        );
        return placeResponse.id();
    }

    private Entry previewCoordinateToken(String coordinateToken, long userId) {
        Entry entry = mapPlaceService.peekCoordinateToken(coordinateToken);
        if (entry == null || entry.userId() != userId) {
            throw new MapException(MapErrorCode.PLACE_COORDINATE_TOKEN_INVALID);
        }
        return entry;
    }

    private String normalizeKakaoPlaceId(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private PostResponse savePost(
            PostUploadRequest request,
            long userId,
            String username,
            PostImageUploadResult uploadResult,
            Long placeId
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            registerRollbackCleanup(uploadResult.key());

            MapPlace mapPlace = placeGrowthService.getPlaceForUpdate(placeId);
            MapImage mapImage = MapImage.builder()
                    .imageUrl(uploadResult.url())
                    .s3Key(uploadResult.key())
                    .title(request.title())
                    .description(request.description())
                    .userId(userId)
                    .username(username)
                    .mapPlace(mapPlace)
                    .build();

            MapImage saved = mapImageRepository.save(mapImage);
            PlaceGrowthSnapshot placeGrowth = placeGrowthService.increasePhotoCount(mapPlace);
            placeRecommendationSnapshotService.refresh(mapPlace.getId());
            return new PostResponse(saved.getId(), saved.getId(), mapPlace.getId(), "게시글을 저장했습니다.", placeGrowth);
        });
    }

    private void registerRollbackCleanup(String uploadedS3Key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    postImageStorage.delete(uploadedS3Key);
                } catch (RuntimeException exception) {
                    log.warn("게시글 업로드 롤백 후 S3 정리에 실패했습니다. key={}", uploadedS3Key, exception);
                }
            }
        });
    }

    private MapException toMapException(PostImageStorageException exception) {
        PostImageStorageError error = exception.getError();
        if (error == PostImageStorageError.NOT_CONFIGURED) {
            return new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }
        if (error == PostImageStorageError.CONNECTION_ERROR) {
            return new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
        return new MapException(MapErrorCode.UPLOAD_ERROR);
    }

    private void publishS3Delete(String s3Key, Long mapImageId, String reason) {
        s3ObjectDeleteOutboxPublisher.publish(
                s3Key,
                "MAP_IMAGE",
                mapImageId == null ? null : String.valueOf(mapImageId),
                reason
        );
    }
}
