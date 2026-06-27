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
import com.typenull.pingdom.post.infrastructure.storage.image.ImageUploadProcessor;
import com.typenull.pingdom.post.infrastructure.storage.image.ProcessedImageUpload;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.util.Arrays;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private static final String MAP_IMAGE_S3_PREFIX = "map/";
    private static final String ORPHAN_REASON = "DB(MapImage)에 존재하지 않는 S3 객체";
    private static final String REPORT_KEY_PREFIX = "pingdom:admin:s3-orphan-report:";
    private static final String LATEST_REPORT_KEY = REPORT_KEY_PREFIX + "latest";
    private static final Duration REPORT_TTL = Duration.ofHours(1);
    private static final int SCAN_BATCH_SIZE = 1_000;

    private final S3ObjectStorage s3ObjectStorage;
    private final StringRedisTemplate redisTemplate;
    private final MapImageRepository mapImageRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PostReportRepository postReportRepository;
    private final UserRepository userRepository;
    private final MapPlaceService mapPlaceService;
    private final PlatformTransactionManager transactionManager;
    private final PlaceGrowthService placeGrowthService;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;
    private final ImageUploadProcessor imageUploadProcessor;
    private final ExecutorService orphanReportExecutor = Executors.newSingleThreadExecutor();

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
    //POST refresh
    //→ 오래 걸리는 비교 작업 시작
    //→ DB s3Key를 Redis set에 저장
    //→ S3 map/ key를 한 페이지씩 읽음
    //→ Redis set에 없으면 고아 파일 후보로 Redis list에 저장
    //
    //GET report
    //→ Redis list에서 page/limit만큼 꺼내서 보여줌
    //
    //DELETE
    //→ 프론트가 확인한 key만 삭제
    public S3OrphanReportStatus refreshMapImageS3OrphanReport() {
        String reportId = UUID.randomUUID().toString();
        String metaKey = reportMetaKey(reportId);
        LocalDateTime now = LocalDateTime.now();

        redisTemplate.opsForHash().put(metaKey, "status", "RUNNING");
        redisTemplate.opsForHash().put(metaKey, "generatedAt", now.toString());
        redisTemplate.expire(metaKey, REPORT_TTL);
        redisTemplate.opsForValue().set(LATEST_REPORT_KEY, reportId, REPORT_TTL);

        orphanReportExecutor.execute(() -> buildMapImageS3OrphanReport(reportId));
        return getMapImageS3OrphanReportStatus(reportId);
    }

    public S3OrphanReport getMapImageS3OrphanReport(String reportId, int page, int limit) {
        String resolvedReportId = resolveReportId(reportId);
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String metaKey = reportMetaKey(resolvedReportId);
        String candidatesKey = reportCandidatesKey(resolvedReportId);
        String status = readMeta(metaKey, "status", "NOT_FOUND");

        long deleteCandidateCount = parseLong(readMeta(metaKey, "deleteCandidateCount", "0"));
        long totalPages = (long) Math.ceil((double) deleteCandidateCount / safeLimit);
        long fromIndex = Math.min((long) (safePage - 1) * safeLimit, deleteCandidateCount);
        long toIndex = Math.min(fromIndex + safeLimit, deleteCandidateCount) - 1;
        List<String> pagedKeys = toIndex < fromIndex
                ? List.of()
                : redisTemplate.opsForList().range(candidatesKey, fromIndex, toIndex);
        List<S3OrphanCandidate> deleteCandidates = (pagedKeys == null ? List.<String>of() : pagedKeys)
                .stream()
                .map(key -> new S3OrphanCandidate(key, ORPHAN_REASON))
                .toList();

        return new S3OrphanReport(
                resolvedReportId,
                status,
                readMeta(metaKey, "errorMessage", null),
                deleteCandidates,
                parseLong(readMeta(metaKey, "dbKeyCount", "0")),
                parseLong(readMeta(metaKey, "s3KeyCount", "0")),
                deleteCandidateCount,
                parseDateTime(readMeta(metaKey, "generatedAt", null)),
                safePage,
                safeLimit,
                deleteCandidateCount,
                totalPages,
                safePage < totalPages
        );
    }

    public S3OrphanReportStatus getMapImageS3OrphanReportStatus(String reportId) {
        String resolvedReportId = resolveReportId(reportId);
        String metaKey = reportMetaKey(resolvedReportId);
        return new S3OrphanReportStatus(
                resolvedReportId,
                readMeta(metaKey, "status", "NOT_FOUND"),
                parseDateTime(readMeta(metaKey, "generatedAt", null)),
                parseDateTime(readMeta(metaKey, "completedAt", null)),
                parseLong(readMeta(metaKey, "dbKeyCount", "0")),
                parseLong(readMeta(metaKey, "s3KeyCount", "0")),
                parseLong(readMeta(metaKey, "deleteCandidateCount", "0")),
                readMeta(metaKey, "errorMessage", null)
        );
    }

    private void buildMapImageS3OrphanReport(String reportId) {
        String metaKey = reportMetaKey(reportId);
        String dbSetKey = reportDbSetKey(reportId);
        String candidatesKey = reportCandidatesKey(reportId);
        long dbKeyCount = 0;
        long s3KeyCount = 0;
        long deleteCandidateCount = 0;

        try {
            redisTemplate.delete(List.of(dbSetKey, candidatesKey));

            dbKeyCount += cacheDbS3Keys(dbSetKey, false);
            dbKeyCount += cacheDbS3Keys(dbSetKey, true);

            // S3 객체도 페이지 단위로 읽어 Redis에 후보 key만 남긴다.
            String continuationToken = null;
            do {
                S3ObjectStorage.S3KeyPage s3KeyPage = s3ObjectStorage.listKeysPage(MAP_IMAGE_S3_PREFIX, continuationToken);
                List<String> candidateKeys = new ArrayList<>();
                for (String key : s3KeyPage.keys()) {
                    if (!StringUtils.hasText(key)) {
                        continue;
                    }
                    s3KeyCount++;
                    Boolean existsInDb = redisTemplate.opsForSet().isMember(dbSetKey, key);
                    if (!Boolean.TRUE.equals(existsInDb)) {
                        candidateKeys.add(key);
                    }
                }
                if (!candidateKeys.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(candidatesKey, candidateKeys);
                    deleteCandidateCount += candidateKeys.size();
                }
                continuationToken = s3KeyPage.nextContinuationToken();
            } while (continuationToken != null);

            redisTemplate.opsForHash().put(metaKey, "status", "COMPLETED");
            redisTemplate.opsForHash().put(metaKey, "completedAt", LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(metaKey, "dbKeyCount", String.valueOf(dbKeyCount));
            redisTemplate.opsForHash().put(metaKey, "s3KeyCount", String.valueOf(s3KeyCount));
            redisTemplate.opsForHash().put(metaKey, "deleteCandidateCount", String.valueOf(deleteCandidateCount));
            expireReportKeys(reportId);
        } catch (RuntimeException exception) {
            log.warn("S3 고아 파일 리포트 생성 실패. reportId={}", reportId, exception);
            redisTemplate.opsForHash().put(metaKey, "status", "FAILED");
            redisTemplate.opsForHash().put(metaKey, "completedAt", LocalDateTime.now().toString());
            redisTemplate.opsForHash().put(metaKey, "errorMessage", exception.getMessage());
            expireReportKeys(reportId);
        } finally {
            redisTemplate.delete(dbSetKey);
        }
    }

    private long cacheDbS3Keys(String dbSetKey, boolean thumbnail) {
        long dbKeyCount = 0;
        int page = 0;
        Slice<String> dbKeySlice;
        do {
            PageRequest pageRequest = PageRequest.of(page, SCAN_BATCH_SIZE);
            dbKeySlice = thumbnail
                    ? mapImageRepository.findThumbnailS3Keys(pageRequest)
                    : mapImageRepository.findS3Keys(pageRequest);
            List<String> dbKeys = dbKeySlice.getContent()
                    .stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (!dbKeys.isEmpty()) {
                redisTemplate.opsForSet().add(dbSetKey, dbKeys.toArray(String[]::new));
                dbKeyCount += dbKeys.size();
            }
            page++;
        } while (dbKeySlice.hasNext());
        return dbKeyCount;
    }

    private String resolveReportId(String reportId) {
        if (StringUtils.hasText(reportId)) {
            return reportId.trim();
        }
        String latestReportId = redisTemplate.opsForValue().get(LATEST_REPORT_KEY);
        if (!StringUtils.hasText(latestReportId)) {
            throw new IllegalStateException("생성된 S3 고아 파일 리포트가 없습니다.");
        }
        return latestReportId;
    }

    private void expireReportKeys(String reportId) {
        redisTemplate.expire(reportMetaKey(reportId), REPORT_TTL);
        redisTemplate.expire(reportCandidatesKey(reportId), REPORT_TTL);
    }

    private String readMeta(String metaKey, String field, String defaultValue) {
        Object value = redisTemplate.opsForHash().get(metaKey, field);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String reportMetaKey(String reportId) {
        return REPORT_KEY_PREFIX + reportId + ":meta";
    }

    private String reportDbSetKey(String reportId) {
        return REPORT_KEY_PREFIX + reportId + ":db-keys";
    }

    private String reportCandidatesKey(String reportId) {
        return REPORT_KEY_PREFIX + reportId + ":candidates";
    }

    @PreDestroy
    void shutdownOrphanReportExecutor() {
        orphanReportExecutor.shutdown();
    }

    public S3OrphanReport createMapImageS3OrphanReport(int page, int limit) {
        return getMapImageS3OrphanReport(null, page, limit);
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

        List<String> deletedKeys = new ArrayList<>();
        List<S3OrphanDeleteFailure> failedKeys = new ArrayList<>();

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

    public record S3OrphanCandidate(String key, String reason) {
    }

    public record S3OrphanReportStatus(
            String reportId,
            String status,
            LocalDateTime generatedAt,
            LocalDateTime completedAt,
            long dbKeyCount,
            long s3KeyCount,
            long deleteCandidateCount,
            String errorMessage
    ) {
    }

    public record S3OrphanReport(
            String reportId,
            String status,
            String errorMessage,
            List<S3OrphanCandidate> deleteCandidates,
            long dbKeyCount,
            long s3KeyCount,
            long deleteCandidateCount,
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
