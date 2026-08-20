package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaCreateRequest;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaResponse;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceMediaService {

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceMediaRepository placeMediaRepository;
    private final MerchantPlaceCapabilityPolicy merchantPlaceCapabilityPolicy;

    @Autowired
    public PlaceMediaService(
            MapPlaceRepository mapPlaceRepository,
            PlaceMediaRepository placeMediaRepository,
            MerchantPlaceCapabilityPolicy merchantPlaceCapabilityPolicy
    ) {
        this.mapPlaceRepository = mapPlaceRepository;
        this.placeMediaRepository = placeMediaRepository;
        this.merchantPlaceCapabilityPolicy = merchantPlaceCapabilityPolicy;
    }

    public PlaceMediaService(
            MapPlaceRepository mapPlaceRepository,
            PlaceMediaRepository placeMediaRepository
    ) {
        this(mapPlaceRepository, placeMediaRepository, null);
    }

    @Transactional
    public PlaceMediaItem createExplorationMedia(Long placeId, Long userId, PlaceMediaCreateRequest request) {
        MapPlace place = getOwnedPlace(placeId, userId);
        int displayOrder = request.displayOrder() == null
                ? placeMediaRepository.findMaxDisplayOrder(placeId, PlaceMediaPurpose.EXPLORATION) + 1
                : request.displayOrder();

        PlaceMedia media = PlaceMedia.exploration(
                place,
                request.imageUrl(),
                request.s3Key(),
                request.thumbnailUrl(),
                request.thumbnailS3Key(),
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
}
