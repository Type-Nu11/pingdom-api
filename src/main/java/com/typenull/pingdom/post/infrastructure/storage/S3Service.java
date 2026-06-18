package com.typenull.pingdom.post.infrastructure.storage;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.api.dto.place.PlaceCreateResponse;
import com.typenull.pingdom.place.application.service.place.MapPlaceService;
import com.typenull.pingdom.place.application.service.place.PlaceGrowthService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.post.api.dto.image.PostUpdateRequest;
import com.typenull.pingdom.post.api.dto.image.PostUpdateResponse;
import com.typenull.pingdom.post.api.dto.image.PostUploadRequest;
import com.typenull.pingdom.post.api.dto.image.PostResponse;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final S3ObjectStorage s3ObjectStorage;
    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final MapPlaceService mapPlaceService;
    private final PlatformTransactionManager transactionManager;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;

    public PostResponse uploadImage(PostUploadRequest request, long userId) {
        Long placeId = resolvePlaceId(request, userId);

        if(mapImageRepository.existsByUserIdAndMapPlace_Id(userId, placeId)){
            throw new MapException(MapErrorCode.ALREADY_POSTED);
        }

        String username = userRepository.findById(userId)
                .map(user -> user.getUsername())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        S3ObjectStorage.S3PutResult putResult;
        try {
            putResult = s3ObjectStorage.put(request.file(), "map");
        } catch (IOException exception) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }

        try {
            return savePost(request, userId, username, putResult, placeId);
        } catch (MapException exception) {
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

        return mapPlaceRepository.findFirstByNameAndAddressAndLatitudeAndLongitude(
                        normalizedPlaceName,
                        normalizedAddress,
                        requestLatitudeFromToken(request.coordinateToken(), userId),
                        requestLongitudeFromToken(request.coordinateToken(), userId)
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

    private Double requestLatitudeFromToken(String coordinateToken, long userId) {
        var entry = previewCoordinateToken(coordinateToken, userId);
        return entry.latitude();
    }

    private Double requestLongitudeFromToken(String coordinateToken, long userId) {
        var entry = previewCoordinateToken(coordinateToken, userId);
        return entry.longitude();
    }

    private com.typenull.pingdom.place.infrastructure.support.PlaceCoordinateTokenStore.Entry previewCoordinateToken(
            String coordinateToken,
            long userId
    ) {
        com.typenull.pingdom.place.infrastructure.support.PlaceCoordinateTokenStore.Entry entry =
                mapPlaceService.peekCoordinateToken(coordinateToken);
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

    public PostUpdateResponse updateImage(PostUpdateRequest request, Long userId, Long imageId) {
        MapImage mapImage = mapImageRepository.findWithMapPlaceById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        if (!Objects.equals(mapImage.getUserId(), userId)) {
            throw new MapException(MapErrorCode.OTHERS_NOT_UPDATE);
        }

        String oldS3Key = mapImage.getS3Key();

        S3ObjectStorage.S3PutResult putResult;
        try {
            putResult = s3ObjectStorage.put(request.file(), "map");
        } catch (IOException exception) {
            throw new MapException(MapErrorCode.UPLOAD_ERROR);
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        PostUpdateResponse response = transactionTemplate.execute(status -> {
            registerRollbackCleanup(putResult.key());

            MapImage imageToUpdate = mapImageRepository.findWithMapPlaceById(imageId)
                    .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

            imageToUpdate.update(
                    request.title(),
                    request.description(),
                    putResult.url(),
                    putResult.key()
            );

            mapImageRepository.save(imageToUpdate);

            return new PostUpdateResponse(imageToUpdate.getId(), "게시글을 수정했습니다.");
        });

        deleteOldS3Quietly(oldS3Key);

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
        deleteFromS3(s3Key);
        PlaceGrowthSnapshot placeGrowth = deletePostRecord(mapImage);

        Long placeId = mapImage.getMapPlace() != null ? mapImage.getMapPlace().getId() : null;
        return new PostResponse(imageId, imageId, placeId, "게시글을 삭제했습니다", placeGrowth);
    }

    private PostResponse savePost(
            PostUploadRequest request,
            long userId,
            String username,
            S3ObjectStorage.S3PutResult putResult,
            Long placeId
    ) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> {
            registerRollbackCleanup(putResult.key());

            MapPlace mapPlace = placeGrowthService.getPlaceForUpdate(placeId);
            MapImage mapImage = MapImage.builder()
                    .imageUrl(putResult.url())
                    .s3Key(putResult.key())
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

    private PlaceGrowthSnapshot deletePostRecord(MapImage mapImage) {
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
            return placeGrowth;
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
                    s3ObjectStorage.delete(uploadedS3Key);
                } catch (RuntimeException exception) {
                    log.warn("게시글 업로드 롤백 후 S3 정리에 실패했습니다. key={}", uploadedS3Key, exception);
                }
            }
        });
    }

    private void deleteFromS3(String s3Key) {
        try {
            s3ObjectStorage.delete(s3Key);
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }
    }

    private MapException toMapException(S3StorageException exception) {
        S3StorageError error = exception.getError();
        if (error == S3StorageError.NOT_CONFIGURED) {
            return new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }
        if (error == S3StorageError.CONNECTION_ERROR) {
            return new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
        return new MapException(MapErrorCode.DELETE_ERROR);
    }

    private void deleteOldS3Quietly(String oldS3Key) {
        if (!StringUtils.hasText(oldS3Key)) {
            return;
        }

        try {
            s3ObjectStorage.delete(oldS3Key);
        } catch (RuntimeException exception) {
            log.warn("게시글 수정 후 기존 S3 파일 삭제에 실패했습니다. key={}", oldS3Key, exception);
        }
    }
}
