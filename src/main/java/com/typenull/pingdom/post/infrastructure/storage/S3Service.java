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
import com.typenull.pingdom.place.infrastructure.support.PlaceCoordinateTokenStore.Entry;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private static final String MAP_IMAGE_S3_PREFIX = "map/";
    private static final String ORPHAN_REASON = "DB(MapImage)에 존재하지 않는 S3 객체";

    private final S3ObjectStorage s3ObjectStorage;
    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final MapPlaceService mapPlaceService;
    private final PlatformTransactionManager transactionManager;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;

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
            publishS3Delete(oldS3Key, imageToUpdate.getId(), "MAP_IMAGE_REPLACED");

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

    public S3OrphanReport createMapImageS3OrphanReport(int page, int limit) {
        // DB에 저장된 MapImage.s3Key를 기준으로 S3의 map/ 객체 중 고아 파일을 찾는다.
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));

        Set<String> dbKeys = mapImageRepository.findAllS3Keys()
                .stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        List<String> s3Keys = s3ObjectStorage.listKeys(MAP_IMAGE_S3_PREFIX)
                .stream()
                .filter(StringUtils::hasText)
                .toList();
        List<S3OrphanCandidate> deleteCandidates = s3Keys.stream()
                .filter(key -> !dbKeys.contains(key))
                .map(key -> new S3OrphanCandidate(key, ORPHAN_REASON))
                .toList();
        int deleteCandidateCount = deleteCandidates.size();
        int totalPages = (int) Math.ceil((double) deleteCandidateCount / safeLimit);
        int fromIndex = Math.min((safePage - 1) * safeLimit, deleteCandidateCount);
        int toIndex = Math.min(fromIndex + safeLimit, deleteCandidateCount);
        List<S3OrphanCandidate> pagedDeleteCandidates = deleteCandidates.subList(fromIndex, toIndex);

        return new S3OrphanReport(
                pagedDeleteCandidates,
                dbKeys.size(),
                s3Keys.size(),
                deleteCandidateCount,
                LocalDateTime.now(),
                safePage,
                safeLimit,
                deleteCandidateCount,
                totalPages,
                safePage < totalPages
        );
    }

    public S3OrphanDeleteResult deleteMapImageS3Keys(List<String> keys) {
        // 리포트를 확인한 뒤 전달받은 key만 삭제한다. 여기서는 후보를 다시 계산하지 않는다.
        Set<String> normalizedKeys = keys == null
                ? Set.of()
                : keys.stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> deletedKeys = new java.util.ArrayList<>();
        List<S3OrphanDeleteFailure> failedKeys = new java.util.ArrayList<>();

        for (String key : normalizedKeys) {
            try {
                s3ObjectStorage.delete(key);
                deletedKeys.add(key);
                log.info("S3 고아 파일 삭제 성공. key={}", key);
            } catch (RuntimeException exception) {
                failedKeys.add(new S3OrphanDeleteFailure(key, exception.getMessage()));
                log.warn("S3 고아 파일 삭제 실패. key={}", key, exception);
            }
        }

        return new S3OrphanDeleteResult(
                normalizedKeys.size(),
                deletedKeys.size(),
                failedKeys.size(),
                deletedKeys,
                failedKeys
        );
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

    private void publishS3Delete(String s3Key, Long mapImageId, String reason) {
        s3ObjectDeleteOutboxPublisher.publish(
                s3Key,
                "MAP_IMAGE",
                mapImageId == null ? null : String.valueOf(mapImageId),
                reason
        );
    }

    public record S3OrphanCandidate(String key, String reason) {
    }

    public record S3OrphanReport(
            List<S3OrphanCandidate> deleteCandidates,
            int dbKeyCount,
            int s3KeyCount,
            int deleteCandidateCount,
            LocalDateTime generatedAt,
            int page,
            int limit,
            long totalCount,
            long totalPages,
            boolean hasNext
    ) {
    }

    public record S3OrphanDeleteFailure(String key, String reason) {
    }

    public record S3OrphanDeleteResult(
            int requestedKeyCount,
            int deletedKeyCount,
            int failedKeyCount,
            List<String> deletedKeys,
            List<S3OrphanDeleteFailure> failedKeys
    ) {
    }
}
