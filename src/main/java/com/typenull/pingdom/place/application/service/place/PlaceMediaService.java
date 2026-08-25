package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaCreateRequest;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaResponse;
import com.typenull.pingdom.place.application.support.PlaceMediaStorageKey;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapability;
import com.typenull.pingdom.identity.application.service.merchant.MerchantPlaceCapabilityPolicy;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectMetadata;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageError;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlaceMediaService {

    private static final long MAX_EXPLORATION_MEDIA_SIZE = 10L * 1024 * 1024;
    private static final List<String> ALLOWED_EXPLORATION_MEDIA_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceMediaRepository placeMediaRepository;
    private final MerchantPlaceCapabilityPolicy merchantPlaceCapabilityPolicy;
    private final S3ObjectStorage s3ObjectStorage;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;

    @Autowired
    public PlaceMediaService(
            MapPlaceRepository mapPlaceRepository,
            PlaceMediaRepository placeMediaRepository,
            MerchantPlaceCapabilityPolicy merchantPlaceCapabilityPolicy,
            S3ObjectStorage s3ObjectStorage,
            S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher
    ) {
        this.mapPlaceRepository = mapPlaceRepository;
        this.placeMediaRepository = placeMediaRepository;
        this.merchantPlaceCapabilityPolicy = merchantPlaceCapabilityPolicy;
        this.s3ObjectStorage = s3ObjectStorage;
        this.s3ObjectDeleteOutboxPublisher = s3ObjectDeleteOutboxPublisher;
    }

    @Transactional
    public PlaceMediaItem createExplorationMedia(Long placeId, Long userId, PlaceMediaCreateRequest request) {
        MapPlace place = getOwnedPlace(placeId, userId);
        String s3Key = validateExplorationObject(placeId, userId, request);
        int displayOrder = request.displayOrder() == null
                ? placeMediaRepository.findMaxDisplayOrder(placeId, PlaceMediaPurpose.EXPLORATION) + 1
                : request.displayOrder();

        PlaceMedia media = PlaceMedia.exploration(
                place,
                s3ObjectStorage.publicUrl(s3Key),
                s3Key,
                null,
                null,
                displayOrder,
                LocalDateTime.now()
        );
        return PlaceMediaItem.from(placeMediaRepository.save(media));
    }

    @Transactional
    public void deleteExplorationMedia(Long placeId, Long mediaId, Long userId) {
        getOwnedPlace(placeId, userId);
        PlaceMedia media = placeMediaRepository
                .findByIdAndPlace_IdAndPurpose(mediaId, placeId, PlaceMediaPurpose.EXPLORATION)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_MEDIA_NOT_FOUND));
        placeMediaRepository.delete(media);
        publishS3Delete(media.getS3Key(), mediaId, "EXPLORATION_MEDIA_DELETED");
        publishS3Delete(media.getThumbnailS3Key(), mediaId, "EXPLORATION_MEDIA_THUMBNAIL_DELETED");
    }

    @Transactional(readOnly = true)
    public PlaceMediaResponse getExplorationMedia(Long placeId) {
        MapPlace place = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        if (!place.isOperating() || !place.isVisibleInDiscovery()) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        return new PlaceMediaResponse(placeId, getMediaItems(placeId, PlaceMediaPurpose.EXPLORATION));
    }

    @Transactional(readOnly = true)
    public PlaceMediaResponse getVerificationMedia(Long placeId, Long userId) {
        getOwnedPlace(placeId, userId);
        return new PlaceMediaResponse(placeId, getMediaItems(placeId, PlaceMediaPurpose.VERIFICATION));
    }

    @Transactional
    public void recordVerificationMedia(MapImage mapImage) {
        if (mapImage == null || mapImage.getMapPlace() == null || mapImage.getId() == null) {
            return;
        }
        if (placeMediaRepository.findBySourceMapImageId(mapImage.getId()).isPresent()) {
            return;
        }

        PlaceMedia media = PlaceMedia.verification(
                mapImage.getMapPlace(),
                mapImage.getImageUrl(),
                mapImage.getS3Key(),
                mapImage.getThumbnailUrl(),
                mapImage.getThumbnailS3Key(),
                mapImage.getId(),
                mapImage.getCreatedAt() == null ? LocalDateTime.now() : mapImage.getCreatedAt()
        );
        placeMediaRepository.save(media);
    }

    private MapPlace getOwnedPlace(Long placeId, Long userId) {
        MapPlace place = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        if (!Objects.equals(place.getUserId(), userId)) {
            try {
                merchantPlaceCapabilityPolicy.require(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
            } catch (RuntimeException exception) {
                throw new MapException(MapErrorCode.OTHERS_PLACE_MEDIA_NOT_MANAGED);
            }
        }
        return place;
    }

    private List<PlaceMediaItem> getMediaItems(Long placeId, PlaceMediaPurpose purpose) {
        return placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(placeId, purpose)
                .stream()
                .map(PlaceMediaItem::from)
                .toList();
    }

    private String validateExplorationObject(Long placeId, Long userId, PlaceMediaCreateRequest request) {
        if (request == null || !PlaceMediaStorageKey.belongsToExplorationMedia(request.s3Key(), placeId, userId)) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }

        S3ObjectMetadata metadata;
        try {
            metadata = s3ObjectStorage.headObject(request.s3Key().trim());
        } catch (S3StorageException exception) {
            throw toMapException(exception);
        }
        if (metadata.contentLength() == null || metadata.contentLength() < 1
                || metadata.contentLength() > MAX_EXPLORATION_MEDIA_SIZE
                || !StringUtils.hasText(metadata.contentType())
                || !ALLOWED_EXPLORATION_MEDIA_TYPES.contains(metadata.contentType().toLowerCase())) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
        return request.s3Key().trim();
    }

    private MapException toMapException(S3StorageException exception) {
        if (exception.getError() == S3StorageError.NOT_CONFIGURED) {
            return new MapException(MapErrorCode.S3_NOT_CONFIGURED);
        }
        if (exception.getError() == S3StorageError.CONNECTION_ERROR) {
            return new MapException(MapErrorCode.S3_CONNECTION_ERROR);
        }
        return new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
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
