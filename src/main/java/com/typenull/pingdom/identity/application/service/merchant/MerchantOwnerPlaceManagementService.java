package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingStatusUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingExceptionRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingTimeRangeRequest;
import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import com.typenull.pingdom.place.application.support.PlaceMediaStorageKey;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.application.service.place.operating.PlaceCurrentOperatingState;
import com.typenull.pingdom.place.application.service.place.operating.PlaceOperatingHoursEvaluator;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.place.media.PlaceMediaPurpose;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectDeleteOutboxPublisher;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MerchantOwnerPlaceManagementService {

    private static final long MAX_UPLOAD_SIZE = 10_485_760L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceMediaRepository placeMediaRepository;
    private final MerchantPlaceCapabilityPolicy capabilityPolicy;
    private final PlaceOperatingHoursEvaluator operatingHoursEvaluator;
    private final S3ObjectStorage s3ObjectStorage;
    private final S3ObjectDeleteOutboxPublisher s3ObjectDeleteOutboxPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MerchantOwnerPlaceDetailResponse getPlace(Long userId, Long placeId) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_VIEW);
        return toDetail(findPlace(placeId));
    }

    @Transactional(readOnly = true)
    public MerchantOwnerOperatingResponse getOperating(Long userId, Long placeId) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_VIEW);
        MapPlace place = findPlace(placeId);
        PlaceCurrentOperatingState current = operatingHoursEvaluator.evaluate(place);
        return new MerchantOwnerOperatingResponse(
                placeId,
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                current.currentlyOperating(),
                current.checkedAt(),
                regularHours(place),
                operatingExceptions(place),
                place.getPrimaryInformationSource(),
                place.getInformationVerificationStatus()
        );
    }

    @Transactional
    public MerchantOwnerOperatingResponse updateOperatingStatus(
            Long userId,
            Long placeId,
            MerchantOwnerOperatingStatusUpdateRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.SCHEDULE_MANAGE);
        if (request == null || request.operatingStatus() == null) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
        MapPlace place = findPlaceForUpdate(placeId);
        LocalDateTime now = LocalDateTime.now(clock);
        place.updateOperatingStatus(request.operatingStatus(), now);
        markOwnerSubmitted(place, now);
        PlaceCurrentOperatingState current = operatingHoursEvaluator.evaluate(place, now);
        return new MerchantOwnerOperatingResponse(
                placeId,
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                current.currentlyOperating(),
                current.checkedAt(),
                regularHours(place),
                operatingExceptions(place),
                place.getPrimaryInformationSource(),
                place.getInformationVerificationStatus()
        );
    }

    @Transactional
    public MerchantOwnerOperatingScheduleResponse updateOperatingSchedule(
            Long userId,
            Long placeId,
            MerchantOwnerOperatingScheduleUpdateRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.SCHEDULE_MANAGE);
        if (request == null) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
        MapPlace place = findPlaceForUpdate(placeId);
        Set<PlaceRegularOperatingHour> regularHours = toRegularHours(request.regularHours());
        List<PlaceOperatingException> exceptions = toExceptions(place, request.exceptions());
        validateSchedule(regularHours, exceptions);
        place.replaceOperatingSchedule(regularHours, exceptions);
        markOwnerSubmitted(place, LocalDateTime.now(clock));
        return new MerchantOwnerOperatingScheduleResponse(
                placeId,
                regularHours(place),
                operatingExceptions(place),
                "장소 영업시간 일정을 수정했습니다."
        );
    }

    @Transactional(readOnly = true)
    public MerchantOwnerMediaResponse getMedia(Long userId, Long placeId) {
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

    @Transactional
    public MerchantOwnerMediaUploadResponse createUploadUrl(
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
        return new MerchantOwnerMediaUploadResponse(
                result.uploadUrl(),
                result.imageUrl(),
                result.key(),
                result.expiresAt()
        );
    }

    @Transactional
    public PlaceMediaItem updateMediaOrder(
            Long userId,
            Long placeId,
            Long mediaId,
            MerchantOwnerMediaOrderUpdateRequest request
    ) {
        requireCapability(userId, placeId, MerchantPlaceCapability.PLACE_INFO_EDIT);
        if (request == null) {
            throw new MapException(MapErrorCode.PLACE_MEDIA_INVALID_REQUEST);
        }
        PlaceMedia media = findExplorationMedia(placeId, mediaId);
        media.updateDisplayOrder(request.displayOrder());
        return PlaceMediaItem.from(media);
    }

    @Transactional
    public MerchantOwnerMediaResponse updateRepresentative(
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

    @Transactional
    public void deleteMedia(Long userId, Long placeId, Long mediaId) {
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
                regularHours(place),
                operatingExceptions(place),
                place.getTouristSummary(),
                place.currentTouristCategories(),
                place.getPrimaryInformationSource(),
                place.getInformationVerificationStatus(),
                place.getInformationVerifiedAt(),
                place.getInformationEvidenceUpdatedAt()
        );
    }

    private List<PlaceRegularOperatingHourResponse> regularHours(MapPlace place) {
        return place.currentRegularOperatingHours().stream()
                .sorted(Comparator.comparing(PlaceRegularOperatingHour::getDayOfWeek)
                        .thenComparing(PlaceRegularOperatingHour::getOpensAt))
                .map(hour -> new PlaceRegularOperatingHourResponse(
                        hour.getDayOfWeek(), hour.getOpensAt(), hour.getClosesAt()
                ))
                .toList();
    }

    private List<PlaceOperatingExceptionResponse> operatingExceptions(MapPlace place) {
        return place.currentOperatingExceptions().stream()
                .sorted(Comparator.comparing(PlaceOperatingException::getExceptionDate))
                .map(exception -> new PlaceOperatingExceptionResponse(
                        exception.getExceptionDate(),
                        exception.isClosed(),
                        exception.currentHours().stream()
                                .sorted(Comparator.comparing(PlaceOperatingTimeRange::getOpensAt))
                                .map(hour -> new PlaceOperatingTimeRangeResponse(hour.getOpensAt(), hour.getClosesAt()))
                                .toList()
                ))
                .toList();
    }

    private List<PlaceMediaItem> explorationMedia(Long placeId) {
        return placeMediaRepository.findAllByPlace_IdAndPurposeOrderByDisplayOrderAscIdAsc(
                        placeId, PlaceMediaPurpose.EXPLORATION
                ).stream()
                .map(PlaceMediaItem::from)
                .toList();
    }

    private Set<PlaceRegularOperatingHour> toRegularHours(
            Set<com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceRegularOperatingHourRequest> requests
    ) {
        if (requests == null) {
            return Set.of();
        }
        return requests.stream()
                .map(request -> PlaceRegularOperatingHour.of(request.dayOfWeek(), request.opensAt(), request.closesAt()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<PlaceOperatingException> toExceptions(
            MapPlace place,
            Set<AdminMapPlaceOperatingExceptionRequest> requests
    ) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .sorted(Comparator.comparing(AdminMapPlaceOperatingExceptionRequest::date))
                .map(request -> {
                    if (request.closed()) {
                        return PlaceOperatingException.closed(place, request.date());
                    }
                    Set<PlaceOperatingTimeRange> hours = request.hours() == null
                            ? Set.of()
                            : request.hours().stream()
                            .map(this::toTimeRange)
                            .collect(java.util.stream.Collectors.toSet());
                    return PlaceOperatingException.customHours(place, request.date(), hours);
                })
                .toList();
    }

    private PlaceOperatingTimeRange toTimeRange(AdminMapPlaceOperatingTimeRangeRequest request) {
        return PlaceOperatingTimeRange.of(request.opensAt(), request.closesAt());
    }

    private void validateSchedule(Set<PlaceRegularOperatingHour> regularHours, List<PlaceOperatingException> exceptions) {
        if (regularHours.stream().anyMatch(hour -> hour.getDayOfWeek() == null
                || hour.getOpensAt() == null || hour.getClosesAt() == null
                || hour.getOpensAt().equals(hour.getClosesAt()))) {
            throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
        Set<LocalDate> dates = new HashSet<>();
        for (PlaceOperatingException exception : exceptions) {
            if (!dates.add(exception.getExceptionDate())
                    || (!exception.isClosed() && exception.currentHours().isEmpty())
                    || (exception.isClosed() && !exception.currentHours().isEmpty())
                    || exception.currentHours().stream().anyMatch(hour -> hour.getOpensAt().equals(hour.getClosesAt()))) {
                throw new MapException(MapErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
        }
    }

    private void markOwnerSubmitted(MapPlace place, LocalDateTime now) {
        place.updateInformationVerification(
                PlaceInformationSourceType.MERCHANT_OWNER,
                PlaceInformationVerificationStatus.OWNER_SUBMITTED,
                null,
                null,
                now
        );
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
