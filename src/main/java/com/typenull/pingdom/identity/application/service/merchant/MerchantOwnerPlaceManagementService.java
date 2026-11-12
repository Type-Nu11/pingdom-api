package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingStatusUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 점주 장소 관리의 공개 진입점과 기존 트랜잭션 경계를 유지합니다. */
@Service
@RequiredArgsConstructor
public class MerchantOwnerPlaceManagementService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MerchantPlaceCapabilityPolicy capabilityPolicy;
    private final MerchantOwnerPlaceOperatingService operatingService;
    private final MerchantOwnerPlaceMediaService mediaService;

    @Transactional(readOnly = true)
    public MerchantOwnerPlaceDetailResponse getPlace(Long userId, Long placeId) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_VIEW);
        return toDetail(findPlace(placeId));
    }

    @Transactional(readOnly = true)
    public MerchantOwnerOperatingResponse getOperating(Long userId, Long placeId) {
        return operatingService.getOperating(userId, placeId);
    }

    @Transactional
    public MerchantOwnerOperatingResponse updateOperatingStatus(
            Long userId,
            Long placeId,
            MerchantOwnerOperatingStatusUpdateRequest request
    ) {
        return operatingService.updateOperatingStatus(userId, placeId, request);
    }

    @Transactional
    public MerchantOwnerOperatingScheduleResponse updateOperatingSchedule(
            Long userId,
            Long placeId,
            MerchantOwnerOperatingScheduleUpdateRequest request
    ) {
        return operatingService.updateOperatingSchedule(userId, placeId, request == null ? null : request.toInput());
    }

    @Transactional(readOnly = true)
    public MerchantOwnerMediaResponse getMedia(Long userId, Long placeId) {
        return mediaService.getMedia(userId, placeId);
    }

    @Transactional
    public MerchantOwnerMediaUploadResponse createUploadUrl(
            Long userId,
            Long placeId,
            MerchantOwnerMediaUploadRequest request
    ) {
        return mediaService.createUploadUrl(userId, placeId, request);
    }

    @Transactional
    public PlaceMediaItem createMedia(
            Long userId,
            Long placeId,
            MerchantOwnerMediaCreateRequest request
    ) {
        return mediaService.createMedia(userId, placeId, request);
    }

    @Transactional
    public PlaceMediaItem updateMediaOrder(
            Long userId,
            Long placeId,
            Long mediaId,
            MerchantOwnerMediaOrderUpdateRequest request
    ) {
        return mediaService.updateMediaOrder(userId, placeId, mediaId, request);
    }

    @Transactional
    public MerchantOwnerMediaResponse updateRepresentative(
            Long userId,
            Long placeId,
            Long mediaId
    ) {
        return mediaService.updateRepresentative(userId, placeId, mediaId);
    }

    @Transactional
    public void deleteMedia(Long userId, Long placeId, Long mediaId) {
        mediaService.deleteMedia(userId, placeId, mediaId);
    }

    private void requireCapability(Long userId, Long placeId, MerchantPlaceCapability capability) {
        capabilityPolicy.require(userId, placeId, capability);
    }

    private MapPlace findPlace(Long placeId) {
        return mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    private MerchantOwnerPlaceDetailResponse toDetail(MapPlace place) {
        return new MerchantOwnerPlaceDetailResponse(
                place.getId(),
                place.getName(),
                place.getEnglishName(),
                place.getCategory(),
                place.getAddress(),
                place.getRoadAddress(),
                place.getJibunAddress(),
                place.getPostalCode(),
                place.getGeocodingSource(),
                place.getLatitude(),
                place.getLongitude(),
                place.getImageUrl(),
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                operatingService.regularHours(place),
                operatingService.operatingExceptions(place),
                place.getDescription(),
                place.getTouristSummary(),
                place.currentTouristCategories(),
                place.getPrimaryInformationSource(),
                place.getInformationVerificationStatus(),
                place.getInformationVerifiedAt(),
                place.getInformationEvidenceUpdatedAt()
        );
    }
}
