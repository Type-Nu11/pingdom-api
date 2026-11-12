package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadResponse;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMediaUpload;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMediaUploadRepository;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.application.support.PlaceMediaStorageKey;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.ImageUploadLimits;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectMetadata;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** facade의 트랜잭션 안에서 업로드 소유권과 탐색 미디어·삭제 Outbox의 변경을 함께 처리합니다. */
@Component
@RequiredArgsConstructor
class MerchantOwnerPlaceMediaService {

    private static final long MAX_UPLOAD_SIZE = ImageUploadLimits.MAX_FILE_SIZE_BYTES;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceMediaRepository placeMediaRepository;
    private final MerchantPlaceMediaUploadRepository mediaUploadRepository;
    private final MerchantPlaceCapabilityPolicy capabilityPolicy;
    private final S3ObjectStorage s3ObjectStorage;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;
    private final Clock clock;

    MerchantOwnerMediaResponse getMedia(Long userId, Long placeId) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_VIEW);
        findPlace(placeId);
        List<PlaceMediaItem> media = explorationMedia(placeId);
        Long representativeMediaId = media.stream()
                .filter(item -> item.imageUrl().equals(findPlace(placeId).getImageUrl()))
                .map(PlaceMediaItem::id)
                .findFirst()
                .orElse(null);
        return new MerchantOwnerMediaResponse(
                placeId,
                representativeMediaId,
                media
        );
    }

    MerchantOwnerMediaUploadResponse createUploadUrl(
            Long userId,
            Long placeId,
            MerchantOwnerMediaUploadRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
        findPlace(placeId);
        validateUpload(request);
        String extension = extension(request.fileName());
        String key = PlaceMediaStorageKey.createExplorationKey(placeId, userId, extension);
        S3ObjectStorage.PresignedPutResult result = s3ObjectStorage.presignedPut(key, request.contentType());
        mediaUploadRepository.save(MerchantPlaceMediaUpload.issue(
                placeId,
                userId,
                result.key(),
                request.contentType(),
                result.expiresAt(),
                LocalDateTime.now(clock)
        ));
        return new MerchantOwnerMediaUploadResponse(
                result.uploadUrl(),
                result.imageUrl(),
                result.key(),
                result.expiresAt()
        );
    }

    PlaceMediaItem createMedia(
            Long userId,
            Long placeId,
            MerchantOwnerMediaCreateRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
        if (request == null || !StringUtils.hasText(request.s3Key())) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }

        MapPlace place = findPlaceForUpdate(placeId);
        String s3Key = request.s3Key().trim();
        MerchantPlaceMediaUpload upload = mediaUploadRepository.findByS3KeyForUpdate(s3Key)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST));
        LocalDateTime now = LocalDateTime.now(clock);
        if (!upload.isRegistrableBy(placeId, userId, now)) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }

        validateUploadedObject(s3Key, upload);
        int displayOrder = request.displayOrder() == null
                ? placeMediaRepository.findMaxDisplayOrder(placeId, PlaceMediaPurpose.EXPLORATION) + 1
                : request.displayOrder();
        PlaceMedia media = placeMediaRepository.save(PlaceMedia.exploration(
                place,
                s3ObjectStorage.publicUrl(s3Key),
                s3Key,
                null,
                null,
                displayOrder,
                now
        ));
        upload.markRegistered(now);
        return PlaceMediaItem.from(media);
    }

    PlaceMediaItem updateMediaOrder(
            Long userId,
            Long placeId,
            Long mediaId,
            MerchantOwnerMediaOrderUpdateRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
        if (request == null) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
        findPlaceForUpdate(placeId);
        List<PlaceMedia> media = explorationMediaForUpdate(placeId);
        int currentIndex = findExplorationMediaIndex(media, mediaId);
        if (request.displayOrder() < 0 || request.displayOrder() >= media.size()) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }

        if (currentIndex == request.displayOrder()) {
            return PlaceMediaItem.from(media.get(currentIndex));
        }

        // 최종 순서를 덮어쓰기 전에 기존 순서를 겹치지 않는 영역으로 옮겨 순서 고유 제약 충돌을 회피.
        int temporaryOffset = temporaryDisplayOrderOffset(media);
        placeMediaRepository.increaseDisplayOrder(placeId, PlaceMediaPurpose.EXPLORATION, temporaryOffset);

        List<PlaceMedia> reorderedMedia = new ArrayList<>(explorationMediaForUpdate(placeId));
        PlaceMedia movedMedia = reorderedMedia.remove(findExplorationMediaIndex(reorderedMedia, mediaId));
        reorderedMedia.add(request.displayOrder(), movedMedia);
        for (int displayOrder = 0; displayOrder < reorderedMedia.size(); displayOrder++) {
            reorderedMedia.get(displayOrder).updateDisplayOrder(displayOrder);
        }
        return PlaceMediaItem.from(movedMedia);
    }

    MerchantOwnerMediaResponse updateRepresentative(
            Long userId,
            Long placeId,
            Long mediaId
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
        MapPlace place = findPlaceForUpdate(placeId);
        PlaceMedia media = findExplorationMedia(placeId, mediaId);
        place.updateImageUrl(media.getImageUrl());
        return getMedia(userId, placeId);
    }

    void deleteMedia(Long userId, Long placeId, Long mediaId) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
        MapPlace place = findPlaceForUpdate(placeId);
        PlaceMedia media = findExplorationMedia(placeId, mediaId);
        if (media.getImageUrl().equals(place.getImageUrl())) {
            place.updateImageUrl(null);
        }
        placeMediaRepository.delete(media);
        publishS3Delete(media.getS3Key(), mediaId, "MERCHANT_EXPLORATION_MEDIA_DELETED");
        publishS3Delete(media.getThumbnailS3Key(), mediaId, "MERCHANT_EXPLORATION_MEDIA_THUMBNAIL_DELETED");
    }

    private void requireCapability(Long userId, Long placeId, MerchantPlaceCapability capability) {
        capabilityPolicy.require(userId, placeId, capability);
    }

    private MapPlace findPlace(Long placeId) {
        return mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private MapPlace findPlaceForUpdate(Long placeId) {
        return mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceMedia findExplorationMedia(Long placeId, Long mediaId) {
        return placeMediaRepository.findByIdAndPlace_IdAndPurpose(mediaId, placeId, PlaceMediaPurpose.EXPLORATION)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_MEDIA_NOT_FOUND));
    }

    private List<PlaceMedia> explorationMediaForUpdate(Long placeId) {
        return placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                placeId,
                PlaceMediaPurpose.EXPLORATION
        );
    }

    private int findExplorationMediaIndex(List<PlaceMedia> media, Long mediaId) {
        for (int index = 0; index < media.size(); index++) {
            if (java.util.Objects.equals(media.get(index).getId(), mediaId)) {
                return index;
            }
        }
        throw new MapException(MapErrorCode.PLACE_MEDIA_NOT_FOUND);
    }

    private int temporaryDisplayOrderOffset(List<PlaceMedia> media) {
        int maxDisplayOrder = media.stream()
                .mapToInt(PlaceMedia::getDisplayOrder)
                .max()
                .orElse(0);
        try {
            return Math.addExact(maxDisplayOrder, Math.addExact(media.size(), 1));
        } catch (ArithmeticException exception) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
    }

    private void validateUploadedObject(String s3Key, MerchantPlaceMediaUpload upload) {
        S3ObjectMetadata metadata;
        try {
            metadata = s3ObjectStorage.headObject(s3Key);
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }
        if (metadata.contentLength() == null
                || metadata.contentLength() <= 0
                || metadata.contentLength() > MAX_UPLOAD_SIZE
                || !upload.matchesContentType(metadata.contentType())
                || !ALLOWED_CONTENT_TYPES.contains(metadata.contentType().toLowerCase())) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
    }

    private MapException toMapException(S3StorageException exception) {
        if (exception.getError() == S3ObjectStorage.S3StorageError.CONNECTION_ERROR) {
            return new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
        return new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
    }

    private List<PlaceMediaItem> explorationMedia(Long placeId) {
        return placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                        placeId, PlaceMediaPurpose.EXPLORATION
                ).stream()
                .map(PlaceMediaItem::from)
                .toList();
    }

    private void validateUpload(MerchantOwnerMediaUploadRequest request) {
        if (request == null || request.fileSize() == null || request.fileSize() > MAX_UPLOAD_SIZE
                || !StringUtils.hasText(request.contentType())
                || !ALLOWED_CONTENT_TYPES.contains(request.contentType().toLowerCase())) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
        String extension = extension(request.fileName());
        if (!Set.of("jpg", "jpeg", "png", "webp").contains(extension)) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private void publishS3Delete(String s3Key, Long mediaId, String reason) {
        if (!StringUtils.hasText(s3Key)) {
            return;
        }
        s3ObjectDeleteOutboxPublisher.publish(
                s3Key,
                "PLACE_MEDIA",
                mediaId == null ? null : String.valueOf(mediaId),
                reason
        );
    }
}
