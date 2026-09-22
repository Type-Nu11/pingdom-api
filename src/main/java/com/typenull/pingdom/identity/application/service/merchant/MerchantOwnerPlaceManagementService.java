package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaOrderUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaCreateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerMediaUploadResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingScheduleUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerOperatingStatusUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceDetailResponse;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMediaUpload;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMediaUploadRepository;
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
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectMetadata;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3StorageException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 장소별 capability를 검사한 뒤 영업 일정과 탐색 이미지를 관리.
 * 등록용 S3 업로드 기록과 장소 미디어를 연결하고 삭제할 객체는 outbox로 전달.
 */
@Service
@RequiredArgsConstructor
public class MerchantOwnerPlaceManagementService {

    private static final long MAX_UPLOAD_SIZE = 10_485_760L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceMediaRepository placeMediaRepository;
    private final MerchantPlaceMediaUploadRepository mediaUploadRepository;
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

    /**
     * 장소 정보 조회 권한과 장소 존재를 확인한 뒤 영업 상태·정규 일정·예외 일정과 현재 영업 여부를 함께 반환.
     * 현재 영업 여부는 평가기로 계산하며 저장된 운영 상태는 유지.
     */
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

    /**
     * 일정 관리 권한과 필수 영업 상태를 확인한 뒤 장소 행을 잠가 운영 상태와 확인 시각을 갱신.
     * 정보 출처를 점주·OWNER_SUBMITTED로 표시하고 같은 시각으로 평가한 현재 영업 상태를 반환.
     */
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

    /**
     * 일정 관리 권한을 확인하고 장소 행을 잠가 정규·예외 영업 일정을 전체 교체.
     * 누락된 요청·잘못된 시간·중복 예외 날짜는 거절하며, 반영 후 점주 제출 정보로 표시하고 정렬된 일정을 반환.
     */
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

    /**
     * 조회 권한과 장소 존재를 확인해 탐색용 미디어를 표시 순서·ID 순으로 반환.
     * 장소 이미지 URL과 일치하는 미디어를 대표 ID로 표시하며 일치하는 항목이 없으면 대표 ID는 null.
     */
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

    /**
     * 편집 권한과 장소를 확인하고 지원 확장자·MIME 및 최대 10MiB 크기 조건을 검사해 S3 업로드 URL을 발급.
     * 발급 키·요청자·장소·만료 시각을 등록용 기록에 저장하고 URL 정보를 반환하며 미디어 행 생성은 이 단계의 처리 범위에서 제외.
     */
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

    /**
     * 발급한 업로드가 요청자·장소에 속하고 아직 등록되지 않았는지 잠금 상태에서 검사.
     * 이미지 바이트 디코딩 없이 S3 HEAD의 크기·MIME을 확인하며 순서 미지정 시 현재 최댓값 뒤에 추가.
     */
    @Transactional
    public PlaceMediaItem createMedia(
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

    /**
     * 편집 권한과 요청 순서 범위를 확인하고 장소 행을 잠가 지정 탐색 미디어를 새 위치로 옮긴 결과를 반환.
     * 같은 위치이면 그대로 반환하며, 변경 시 기존 순서를 임시 범위로 이동한 뒤 0부터 재배열해 고유 제약 충돌을 회피.
     */
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

    /**
     * 편집 권한을 확인하고 장소 행을 잠근 뒤 그 장소의 탐색 미디어 URL을 대표 이미지로 설정.
     * 장소나 대상 미디어가 없으면 거절하고, 변경된 대표 ID와 미디어 목록을 반환.
     */
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

    /**
     * 편집 권한을 확인하고 장소 행을 잠가 탐색 미디어를 삭제하며 대표 이미지와 같으면 대표 URL도 비움.
     * 원본·썸네일 키가 있는 경우 각각 S3 삭제 Outbox를 발행하고, 없는 장소나 미디어는 거절.
     */
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
                place.getDescription(),
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
