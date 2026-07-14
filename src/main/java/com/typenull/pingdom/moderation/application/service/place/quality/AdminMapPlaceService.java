package com.typenull.pingdom.moderation.application.service.place.quality;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryItem;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeRestoreResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingExceptionRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingTimeRangeRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceRegularOperatingHourRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficPolicyItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic.AdminPlaceRecommendationTrafficUpdateResponse;
import com.typenull.pingdom.moderation.application.AdminPostService;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.place.AdminPlaceMergeHistory;
import com.typenull.pingdom.moderation.domain.recommendation.AdminRecommendationPolicyChangeHistory;
import com.typenull.pingdom.moderation.domain.recommendation.AdminRecommendationPolicyChangeType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminPlaceMergeHistoryRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminRecommendationPolicyChangeHistoryRepository;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.application.service.recommendation.policy.PlaceRecommendationPolicyService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.outbox.PlaceRecommendationResyncOutboxPayload;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMapPlaceService {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);
    private static final long NANOS_PER_DAY = 24L * 60 * 60 * 1_000_000_000;

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceEventRepository placeEventRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final AdminPostService adminPostService;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;
    private final PlaceRecommendationPolicyService placeRecommendationPolicyService;
    private final PlaceRecommendationSnapshotResyncService placeRecommendationSnapshotResyncService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AdminPlaceDuplicateResolver adminPlaceDuplicateResolver;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminPlaceMergeHistoryRepository adminPlaceMergeHistoryRepository;
    private final AdminRecommendationPolicyChangeHistoryRepository adminRecommendationPolicyChangeHistoryRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Transactional
    public void deletePlace(long placeId, Long adminUserId) {
        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        if (placeEventRepository.existsByPlace_Id(placeId)) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_CONNECTED);
        }
        Map<String, Object> beforeState = placeState(mapPlace);
        List<Long> linkedPostIds = mapImageRepository.findIdsByMapPlaceId(placeId);

        linkedPostIds.forEach(postId -> adminPostService.deletePost(postId, adminUserId));

        mapPlaceRepository.delete(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_DELETED,
                AdminAuditTargetType.PLACE,
                placeId,
                "PLACE_DELETED",
                beforeState,
                Map.of("placeId", placeId, "deleted", true, "deletedPostCount", linkedPostIds.size())
        );
    }

    @Transactional
    public AdminPlaceRecommendationTrafficUpdateResponse updateRecommendationTraffic(
            Long adminUserId,
            AdminPlaceRecommendationTrafficUpdateRequest request
    ) {
        validateRecommendationTrafficRequest(request);
        List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> beforePolicies =
                placeRecommendationPolicyService.getTrafficPolicies();

        Map<String, Integer> requestedTrafficByVersion = new LinkedHashMap<>();
        Map<String, PlaceRecommendationPolicyService.PolicyUpdateCommand> policyCommands = new LinkedHashMap<>();
        for (AdminPlaceRecommendationTrafficUpdateItem policy : request.policies()) {
            String recommendationVersion = policy.recommendationVersion().trim();
            if (!placeRecommendationPolicyService.supportsVersion(recommendationVersion)) {
                throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_VERSION_NOT_FOUND);
            }
            if (requestedTrafficByVersion.putIfAbsent(recommendationVersion, policy.trafficPercentage()) != null) {
                throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
            }
            boolean enabled = policy.enabled() == null || policy.enabled();
            String fallbackVersion = trimToNull(policy.fallbackVersion());
            if (!enabled) {
                if (!StringUtils.hasText(fallbackVersion)) {
                    throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
                }
                if (!placeRecommendationPolicyService.supportsVersion(fallbackVersion)
                        || recommendationVersion.equals(fallbackVersion)) {
                    throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
                }
            } else {
                fallbackVersion = null;
            }
            policyCommands.put(
                    recommendationVersion,
                    new PlaceRecommendationPolicyService.PolicyUpdateCommand(
                            policy.trafficPercentage(),
                            enabled,
                            fallbackVersion
                    )
            );
        }
        if (requestedTrafficByVersion.size() != beforePolicies.size()) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID);
        }
        validateFallbackCycle(policyCommands);

        long enabledPolicyCount = policyCommands.values().stream()
                .filter(PlaceRecommendationPolicyService.PolicyUpdateCommand::enabled)
                .count();
        if (enabledPolicyCount == 0) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
        }

        int totalTrafficPercentage = requestedTrafficByVersion.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (totalTrafficPercentage != 100) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID);
        }

        List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> updatedPolicies =
                placeRecommendationPolicyService.updateTrafficPolicies(policyCommands);
        List<AdminRecommendationPolicyChangeHistory> policyHistories = buildRecommendationPolicyHistories(
                adminUserId,
                request.reason().trim(),
                beforePolicies,
                updatedPolicies
        );
        if (!policyHistories.isEmpty()) {
            adminRecommendationPolicyChangeHistoryRepository.saveAll(policyHistories);
        }

        adminAuditLogService.record(
                adminUserId,
                updatedPolicies.stream().anyMatch(policy -> !policy.enabled())
                        ? AdminAuditAction.PLACE_RECOMMENDATION_KILL_SWITCH_UPDATED
                        : AdminAuditAction.PLACE_RECOMMENDATION_TRAFFIC_UPDATED,
                AdminAuditTargetType.PLACE,
                placeRecommendationPolicyService.getDefaultVersion(),
                request.reason().trim(),
                toTrafficAuditState(beforePolicies),
                toTrafficAuditState(updatedPolicies)
        );

        log.info(
                "Admin updated recommendation traffic. adminUserId={}, policies={}",
                adminUserId,
                requestedTrafficByVersion
        );

        return new AdminPlaceRecommendationTrafficUpdateResponse(
                placeRecommendationPolicyService.getDefaultVersion(),
                updatedPolicies.stream()
                        .map(policy -> new AdminPlaceRecommendationTrafficPolicyItem(
                                policy.version(),
                                policy.stage().name(),
                                policy.trafficPercentage(),
                                policy.enabled(),
                                policy.fallbackVersion()
                        ))
                        .toList(),
                "추천 버전 트래픽 비율을 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceCoordinateUpdateResponse updatePlaceCoordinates(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceCoordinateUpdateRequest request
    ) {
        if (request == null || request.latitude() == null || request.longitude() == null) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        Double beforeLatitude = mapPlace.getLatitude();
        Double beforeLongitude = mapPlace.getLongitude();

        mapPlace.updateGeocoding(
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                request.latitude(),
                request.longitude(),
                toPoint(request.latitude(), request.longitude()),
                GeocodingSource.ADMIN
        );

        requestRecommendationResync(mapPlace, "ADMIN_COORDINATE_UPDATED");

        log.info(
                "Admin updated place coordinates. adminUserId={}, placeId={}, beforeLatitude={}, beforeLongitude={}, afterLatitude={}, afterLongitude={}",
                adminUserId,
                placeId,
                beforeLatitude,
                beforeLongitude,
                request.latitude(),
                request.longitude()
        );

        return new AdminMapPlaceCoordinateUpdateResponse(
                mapPlace.getId(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                "장소 좌표를 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceGeocodingUpdateResponse updatePlaceGeocoding(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceGeocodingUpdateRequest request
    ) {
        if (request == null || !StringUtils.hasText(request.address())
                || request.latitude() == null || request.longitude() == null) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        Map<String, Object> beforeState = geocodingState(mapPlace);
        String normalizedRoadAddress = trimToNull(request.roadAddress());
        String normalizedJibunAddress = trimToNull(request.jibunAddress());
        String representativeAddress = normalizedRoadAddress != null
                ? normalizedRoadAddress
                : normalizedJibunAddress != null ? normalizedJibunAddress : request.address().trim();

        mapPlace.updateGeocoding(
                representativeAddress,
                normalizedRoadAddress,
                normalizedJibunAddress,
                trimToNull(request.postalCode()),
                request.latitude(),
                request.longitude(),
                toPoint(request.latitude(), request.longitude()),
                GeocodingSource.ADMIN
        );
        Map<String, Object> afterState = geocodingState(mapPlace);

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_GEOCODING_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );
        requestRecommendationResync(mapPlace, "ADMIN_GEOCODING_UPDATED");

        log.info("Admin updated place geocoding. adminUserId={}, placeId={}", adminUserId, placeId);
        return new AdminMapPlaceGeocodingUpdateResponse(
                mapPlace.getId(),
                mapPlace.getAddress(),
                mapPlace.getRoadAddress(),
                mapPlace.getJibunAddress(),
                mapPlace.getPostalCode(),
                mapPlace.getGeocodingSource(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                "장소 주소와 좌표를 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceKakaoPlaceIdUpdateResponse updatePlaceKakaoPlaceId(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceKakaoPlaceIdUpdateRequest request
    ) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        String normalizedKakaoPlaceId = trimToNull(request == null ? null : request.kakaoPlaceId());
        String beforeKakaoPlaceId = mapPlace.getKakaoPlaceId();

        if (normalizedKakaoPlaceId != null) {
            mapPlaceRepository.findByKakaoPlaceIdAndIdNot(normalizedKakaoPlaceId, placeId)
                    .ifPresent(ignored -> {
                        throw new AdminException(AdminErrorCode.PLACE_KAKAO_PLACE_ID_CONFLICT);
                    });
        }

        mapPlace.updateKakaoPlaceId(normalizedKakaoPlaceId);
        Map<String, Object> beforeState = new LinkedHashMap<>();
        beforeState.put("placeId", placeId);
        beforeState.put("kakaoPlaceId", beforeKakaoPlaceId);

        Map<String, Object> afterState = new LinkedHashMap<>();
        afterState.put("placeId", placeId);
        afterState.put("kakaoPlaceId", normalizedKakaoPlaceId);

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_KAKAO_PLACE_ID_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                "PLACE_KAKAO_PLACE_ID_UPDATED",
                beforeState,
                afterState
        );

        log.info(
                "Admin updated place kakaoPlaceId. adminUserId={}, placeId={}, beforeKakaoPlaceId={}, afterKakaoPlaceId={}",
                adminUserId,
                placeId,
                beforeKakaoPlaceId,
                normalizedKakaoPlaceId
        );

        return new AdminMapPlaceKakaoPlaceIdUpdateResponse(
                mapPlace.getId(),
                mapPlace.getKakaoPlaceId(),
                "장소 Kakao place id를 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceTouristInfoUpdateResponse updatePlaceTouristInfo(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceTouristInfoUpdateRequest request
    ) {
        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        String normalizedEnglishName = trimToNull(request.englishName());
        String normalizedTouristSummary = trimToNull(request.touristSummary());
        Set<TouristCategory> normalizedTouristCategories = normalizeTouristCategories(request.touristCategories());
        Map<String, Object> beforeState = touristInfoState(mapPlace);
        int beforeTouristCategoryCount = mapPlace.currentTouristCategories().size();

        mapPlace.updateTouristInformation(
                normalizedEnglishName,
                normalizedTouristSummary,
                normalizedTouristCategories
        );

        Map<String, Object> afterState = touristInfoState(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_TOURIST_INFO_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );

        log.info(
                "Admin updated place tourist information. adminUserId={}, placeId={}, englishNameChanged={}, touristSummaryChanged={}, beforeTouristCategoryCount={}, afterTouristCategoryCount={}",
                adminUserId,
                placeId,
                !Objects.equals(beforeState.get("englishName"), normalizedEnglishName),
                !Objects.equals(beforeState.get("touristSummary"), normalizedTouristSummary),
                beforeTouristCategoryCount,
                normalizedTouristCategories.size()
        );

        return new AdminMapPlaceTouristInfoUpdateResponse(
                mapPlace.getId(),
                mapPlace.getEnglishName(),
                mapPlace.getTouristSummary(),
                mapPlace.currentTouristCategories(),
                "장소 관광 정보를 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceOperatingStatusUpdateResponse updatePlaceOperatingStatus(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceOperatingStatusUpdateRequest request
    ) {
        if (request == null || request.operatingStatus() == null || !StringUtils.hasText(request.reason())) {
            throw new AdminException(AdminErrorCode.PLACE_OPERATING_STATUS_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        Map<String, Object> beforeState = operatingStatusState(mapPlace);
        LocalDateTime checkedAt = now();

        mapPlace.updateOperatingStatus(request.operatingStatus(), checkedAt);

        Map<String, Object> afterState = operatingStatusState(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_OPERATING_STATUS_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );

        log.info(
                "Admin updated place operating status. adminUserId={}, placeId={}, operatingStatus={}, checkedAt={}",
                adminUserId,
                placeId,
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt()
        );

        return new AdminMapPlaceOperatingStatusUpdateResponse(
                mapPlace.getId(),
                mapPlace.getOperatingStatus(),
                mapPlace.getOperatingStatusCheckedAt(),
                "장소 운영 상태를 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceOperatingScheduleUpdateResponse updatePlaceOperatingSchedule(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceOperatingScheduleUpdateRequest request
    ) {
        validateOperatingScheduleRequest(request);

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        Map<String, Object> beforeState = operatingScheduleState(mapPlace);

        mapPlace.replaceOperatingSchedule(
                toRegularOperatingHours(request.regularHours()),
                toOperatingExceptions(mapPlace, request.exceptions())
        );

        Map<String, Object> afterState = operatingScheduleState(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_OPERATING_SCHEDULE_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );

        log.info(
                "Admin updated place operating schedule. adminUserId={}, placeId={}, regularHourCount={}, exceptionCount={}",
                adminUserId,
                placeId,
                mapPlace.currentRegularOperatingHours().size(),
                mapPlace.currentOperatingExceptions().size()
        );

        return new AdminMapPlaceOperatingScheduleUpdateResponse(
                mapPlace.getId(),
                regularHours(mapPlace),
                operatingExceptions(mapPlace),
                "장소 영업시간 일정을 수정했습니다."
        );
    }

    @Transactional
    public AdminMapPlaceMergeResponse mergePlaces(Long adminUserId, AdminMapPlaceMergeRequest request) {
        validateMergeRequest(request);

        List<Long> orderedIds = List.of(request.sourcePlaceId(), request.targetPlaceId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        MapPlace firstLockedPlace = mapPlaceRepository.findByIdForUpdate(orderedIds.get(0))
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        MapPlace secondLockedPlace = mapPlaceRepository.findByIdForUpdate(orderedIds.get(1))
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        MapPlace sourcePlace = firstLockedPlace.getId().equals(request.sourcePlaceId()) ? firstLockedPlace : secondLockedPlace;
        MapPlace targetPlace = firstLockedPlace.getId().equals(request.targetPlaceId()) ? firstLockedPlace : secondLockedPlace;

        if (placeEventRepository.existsByPlace_Id(sourcePlace.getId())) {
            throw new AdminException(AdminErrorCode.PLACE_EVENT_CONNECTED);
        }
        if (!adminPlaceDuplicateResolver.areDuplicates(sourcePlace, targetPlace)) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_NOT_ALLOWED);
        }
        Map<String, Object> beforeState = placeMergeBeforeState(sourcePlace, targetPlace);
        MergeExecutionContext mergeExecutionContext = new MergeExecutionContext(
                placeSnapshot(sourcePlace),
                placeSnapshot(targetPlace)
        );

        transferKakaoPlaceIdIfNeeded(sourcePlace, targetPlace);

        long movedImageCount = reassignImages(sourcePlace, targetPlace, mergeExecutionContext);
        BookmarkMergeResult bookmarkMergeResult = reassignBookmarks(sourcePlace, targetPlace, mergeExecutionContext);
        ConversionMergeResult conversionMergeResult = reassignConversions(sourcePlace, targetPlace, mergeExecutionContext);
        int movedClickCount = reassignClicks(sourcePlace, targetPlace, mergeExecutionContext);
        int movedExposureCount = reassignExposures(sourcePlace, targetPlace, mergeExecutionContext);
        int movedFeatureLogCount = reassignFeatureLogs(sourcePlace, targetPlace, mergeExecutionContext);

        targetPlace.replacePhotoCount(countActiveImages(targetPlace.getId()));
        mapPlaceRepository.delete(sourcePlace);
        adminPlaceMergeHistoryRepository.save(mergeExecutionContext.toHistory(adminUserId));
        placeRecommendationSnapshotResyncService.resyncMergedPlace(sourcePlace.getId(), targetPlace.getId());
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_MERGED,
                AdminAuditTargetType.PLACE,
                targetPlace.getId(),
                "PLACE_MERGED",
                beforeState,
                placeMergeAfterState(
                        sourcePlace.getId(),
                        targetPlace,
                        movedImageCount,
                        bookmarkMergeResult,
                        conversionMergeResult,
                        movedClickCount,
                        movedExposureCount,
                        movedFeatureLogCount
                )
        );

        log.info(
                "Admin place merge completed. adminUserId={}, sourcePlaceId={}, targetPlaceId={}, movedImageCount={}, movedBookmarkCount={}, removedBookmarkCount={}, movedConversionCount={}, removedConversionCount={}, movedClickCount={}, movedExposureCount={}, movedFeatureLogCount={}",
                adminUserId,
                sourcePlace.getId(),
                targetPlace.getId(),
                movedImageCount,
                bookmarkMergeResult.movedCount(),
                bookmarkMergeResult.deletedCount(),
                conversionMergeResult.movedCount(),
                conversionMergeResult.deletedCount(),
                movedClickCount,
                movedExposureCount,
                movedFeatureLogCount
        );

        return new AdminMapPlaceMergeResponse(
                sourcePlace.getId(),
                targetPlace.getId(),
                "중복 장소를 병합했습니다."
        );
    }

    @Transactional(readOnly = true)
    public AdminPlaceMergeHistoryResponse listMergeHistories() {
        List<AdminPlaceMergeHistoryItem> histories = adminPlaceMergeHistoryRepository.findTop50ByOrderByMergedAtDescIdDesc().stream()
                .map(history -> new AdminPlaceMergeHistoryItem(
                        history.getId(),
                        history.getSourcePlaceId(),
                        history.getTargetPlaceId(),
                        history.getAdminUserId(),
                        history.isRestored(),
                        history.getMergedAt(),
                        history.getRestoredAt()
                ))
                .toList();
        return new AdminPlaceMergeHistoryResponse(histories);
    }

    @Transactional
    public AdminPlaceMergeRestoreResponse restoreMerge(Long adminUserId, Long historyId) {
        AdminPlaceMergeHistory history = adminPlaceMergeHistoryRepository.findByIdForUpdate(historyId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_MERGE_HISTORY_NOT_FOUND));
        if (history.isRestored()) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_ALREADY_RESTORED);
        }

        PlaceSnapshot sourceSnapshot = readValue(history.getSourcePlaceSnapshot(), new TypeReference<>() {
        });
        PlaceSnapshot targetSnapshot = readValue(history.getTargetPlaceSnapshot(), new TypeReference<>() {
        });

        if (mapPlaceRepository.existsById(sourceSnapshot.id())) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
        }

        MapPlace targetPlace = mapPlaceRepository.findByIdForUpdate(history.getTargetPlaceId())
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED));
        prepareTargetBeforeRestore(targetPlace, sourceSnapshot, targetSnapshot);

        MapPlace restoredSourcePlace = insertRestoredPlace(sourceSnapshot);
        restoreImages(restoredSourcePlace, readLongList(history.getMovedImageIds()));
        restoreBookmarks(restoredSourcePlace, history.getMovedBookmarkIds(), history.getDeletedBookmarks());
        restoreConversions(restoredSourcePlace, history.getMovedConversionIds(), history.getDeletedConversions());
        restoreClicks(restoredSourcePlace.getId(), readLongList(history.getMovedClickIds()));
        restoreExposures(restoredSourcePlace.getId(), readLongList(history.getMovedExposureIds()));
        restoreFeatureLogs(restoredSourcePlace.getId(), readLongList(history.getMovedFeatureLogIds()));
        restoreKakaoPlaceIds(restoredSourcePlace, targetPlace, sourceSnapshot, targetSnapshot);

        restoredSourcePlace.replacePhotoCount(countActiveImages(restoredSourcePlace.getId()));
        targetPlace.replacePhotoCount(countActiveImages(targetPlace.getId()));
        history.markRestored(now());

        placeRecommendationSnapshotResyncService.resyncMergedPlace(restoredSourcePlace.getId(), targetPlace.getId());
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_MERGED,
                AdminAuditTargetType.PLACE,
                restoredSourcePlace.getId(),
                "PLACE_MERGE_RESTORED",
                Map.of("historyId", historyId, "targetPlaceId", targetPlace.getId()),
                Map.of("historyId", historyId, "sourcePlaceId", restoredSourcePlace.getId(), "restored", true)
        );

        return new AdminPlaceMergeRestoreResponse(
                history.getId(),
                restoredSourcePlace.getId(),
                targetPlace.getId(),
                "장소 병합을 복구했습니다."
        );
    }

    @Transactional
    public PlaceRecommendationSnapshotResyncService.SnapshotResyncResult resyncRecommendationSnapshots() {
        return placeRecommendationSnapshotResyncService.resyncAll();
    }

    private void validateMergeRequest(AdminMapPlaceMergeRequest request) {
        if (request == null || request.sourcePlaceId() == null || request.targetPlaceId() == null) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
        if (request.sourcePlaceId().equals(request.targetPlaceId())) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
    }

    private long reassignImages(MapPlace sourcePlace, MapPlace targetPlace, MergeExecutionContext mergeExecutionContext) {
        List<MapImage> sourceImages = mapImageRepository.findByMapPlace_Id(sourcePlace.getId());
        sourceImages.forEach(image -> {
            mergeExecutionContext.movedImageIds().add(image.getId());
            image.reassignPlace(targetPlace);
        });
        return sourceImages.size();
    }

    private BookmarkMergeResult reassignBookmarks(
            MapPlace sourcePlace,
            MapPlace targetPlace,
            MergeExecutionContext mergeExecutionContext
    ) {
        List<MapBookmark> sourceBookmarks = mapBookmarkRepository.findByPlaceId(sourcePlace.getId());
        Set<Long> targetBookmarkUserIds = new HashSet<>();
        for (MapBookmark targetBookmark : mapBookmarkRepository.findByPlaceId(targetPlace.getId())) {
            targetBookmarkUserIds.add(targetBookmark.getUserId());
        }

        int movedCount = 0;
        int deletedCount = 0;
        for (MapBookmark sourceBookmark : sourceBookmarks) {
            if (targetBookmarkUserIds.contains(sourceBookmark.getUserId())) {
                mergeExecutionContext.deletedBookmarks().add(new BookmarkSnapshot(sourceBookmark.getUserId()));
                mapBookmarkRepository.delete(sourceBookmark);
                deletedCount++;
                continue;
            }
            mergeExecutionContext.movedBookmarkIds().add(sourceBookmark.getId());
            sourceBookmark.reassignPlace(targetPlace.getId());
            targetBookmarkUserIds.add(sourceBookmark.getUserId());
            movedCount++;
        }
        return new BookmarkMergeResult(movedCount, deletedCount);
    }

    private ConversionMergeResult reassignConversions(
            MapPlace sourcePlace,
            MapPlace targetPlace,
            MergeExecutionContext mergeExecutionContext
    ) {
        List<PlaceRecommendationConversion> sourceConversions =
                placeRecommendationConversionRepository.findByPlaceId(sourcePlace.getId());
        List<PlaceRecommendationConversion> targetConversions =
                placeRecommendationConversionRepository.findByPlaceId(targetPlace.getId());

        record ConversionKey(Long userId, PlaceRecommendationConversionType conversionType) {
        }

        Set<ConversionKey> targetConversionKeys = new HashSet<>();
        for (PlaceRecommendationConversion targetConversion : targetConversions) {
            targetConversionKeys.add(new ConversionKey(
                    targetConversion.getUserId(),
                    targetConversion.getConversionType()
            ));
        }

        int movedCount = 0;
        int deletedCount = 0;
        for (PlaceRecommendationConversion sourceConversion : sourceConversions) {
            ConversionKey conversionKey = new ConversionKey(
                    sourceConversion.getUserId(),
                    sourceConversion.getConversionType()
            );
            if (targetConversionKeys.contains(conversionKey)) {
                mergeExecutionContext.deletedConversions().add(ConversionSnapshot.from(sourceConversion));
                placeRecommendationConversionRepository.delete(sourceConversion);
                deletedCount++;
                continue;
            }
            mergeExecutionContext.movedConversionIds().add(sourceConversion.getId());
            sourceConversion.reassignPlace(targetPlace.getId());
            targetConversionKeys.add(conversionKey);
            movedCount++;
        }
        return new ConversionMergeResult(movedCount, deletedCount);
    }

    private int reassignClicks(MapPlace sourcePlace, MapPlace targetPlace, MergeExecutionContext mergeExecutionContext) {
        List<Long> clickIds = placeRecommendationClickRepository.findIdsByPlaceId(sourcePlace.getId());
        mergeExecutionContext.movedClickIds().addAll(clickIds);
        return placeRecommendationClickRepository.updatePlaceId(sourcePlace.getId(), targetPlace.getId());
    }

    private int reassignExposures(MapPlace sourcePlace, MapPlace targetPlace, MergeExecutionContext mergeExecutionContext) {
        List<Long> exposureIds = placeRecommendationExposureRepository.findIdsByPlaceId(sourcePlace.getId());
        mergeExecutionContext.movedExposureIds().addAll(exposureIds);
        return placeRecommendationExposureRepository.updatePlaceId(sourcePlace.getId(), targetPlace.getId());
    }

    private int reassignFeatureLogs(MapPlace sourcePlace, MapPlace targetPlace, MergeExecutionContext mergeExecutionContext) {
        List<Long> featureLogIds = placeRecommendationFeatureLogRepository.findIdsByPlaceId(sourcePlace.getId());
        mergeExecutionContext.movedFeatureLogIds().addAll(featureLogIds);
        return placeRecommendationFeatureLogRepository.updatePlaceId(sourcePlace.getId(), targetPlace.getId());
    }

    private record BookmarkMergeResult(int movedCount, int deletedCount) {
    }

    private record ConversionMergeResult(int movedCount, int deletedCount) {
    }

    private Map<String, Object> placeState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("name", place.getName());
        state.put("address", place.getAddress());
        state.put("roadAddress", place.getRoadAddress());
        state.put("jibunAddress", place.getJibunAddress());
        state.put("postalCode", place.getPostalCode());
        state.put("geocodingSource", place.getGeocodingSource());
        state.put("operatingStatus", place.getOperatingStatus());
        state.put("operatingStatusCheckedAt", place.getOperatingStatusCheckedAt());
        state.put("regularHours", regularHours(place));
        state.put("operatingExceptions", operatingExceptions(place));
        state.put("category", place.getCategory());
        state.put("englishName", place.getEnglishName());
        state.put("touristSummary", place.getTouristSummary());
        state.put("touristCategories", normalizeTouristCategories(place.currentTouristCategories()));
        state.put("kakaoPlaceId", place.getKakaoPlaceId());
        state.put("latitude", place.getLatitude());
        state.put("longitude", place.getLongitude());
        state.put("userId", place.getUserId());
        state.put("registrant", place.getRegistrant());
        state.put("photoCount", place.currentPhotoCount());
        return state;
    }

    private Map<String, Object> geocodingState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("address", place.getAddress());
        state.put("roadAddress", place.getRoadAddress());
        state.put("jibunAddress", place.getJibunAddress());
        state.put("postalCode", place.getPostalCode());
        state.put("geocodingSource", place.getGeocodingSource());
        state.put("latitude", place.getLatitude());
        state.put("longitude", place.getLongitude());
        return state;
    }

    private void requestRecommendationResync(MapPlace place, String reason) {
        String deduplicationKey = "PLACE_RECOMMENDATION_RESYNC:%d:%s".formatted(
                place.getId(),
                UUID.randomUUID()
        );
        outboxEventPublisher.publish(
                deduplicationKey,
                OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED,
                new PlaceRecommendationResyncOutboxPayload(place.getId(), reason),
                "MAP_PLACE",
                String.valueOf(place.getId())
        );
    }

    private Map<String, Object> placeMergeBeforeState(MapPlace sourcePlace, MapPlace targetPlace) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("sourcePlace", placeState(sourcePlace));
        state.put("targetPlace", placeState(targetPlace));
        return state;
    }

    private Map<String, Object> placeMergeAfterState(
            Long sourcePlaceId,
            MapPlace targetPlace,
            long movedImageCount,
            BookmarkMergeResult bookmarkMergeResult,
            ConversionMergeResult conversionMergeResult,
            int movedClickCount,
            int movedExposureCount,
            int movedFeatureLogCount
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("sourcePlaceId", sourcePlaceId);
        state.put("sourcePlaceDeleted", true);
        state.put("targetPlace", placeState(targetPlace));
        state.put("movedImageCount", movedImageCount);
        state.put("movedBookmarkCount", bookmarkMergeResult.movedCount());
        state.put("deletedBookmarkCount", bookmarkMergeResult.deletedCount());
        state.put("movedConversionCount", conversionMergeResult.movedCount());
        state.put("deletedConversionCount", conversionMergeResult.deletedCount());
        state.put("movedClickCount", movedClickCount);
        state.put("movedExposureCount", movedExposureCount);
        state.put("movedFeatureLogCount", movedFeatureLogCount);
        return state;
    }

    private long countActiveImages(Long placeId) {
        return mapImageRepository.countByMapPlace_IdAndVisibilityStatus(placeId, MapImageVisibilityStatus.ACTIVE);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static Point toPoint(double latitude, double longitude) {
        return WGS84.createPoint(new Coordinate(longitude, latitude));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Set<TouristCategory> normalizeTouristCategories(Set<TouristCategory> touristCategories) {
        Set<TouristCategory> normalizedCategories = EnumSet.noneOf(TouristCategory.class);
        if (touristCategories != null) {
            normalizedCategories.addAll(touristCategories);
        }
        return normalizedCategories;
    }

    private Map<String, Object> touristInfoState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("englishName", place.getEnglishName());
        state.put("touristSummary", place.getTouristSummary());
        state.put("touristCategories", normalizeTouristCategories(place.currentTouristCategories()));
        return state;
    }

    private Map<String, Object> operatingStatusState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("operatingStatus", place.getOperatingStatus());
        state.put("operatingStatusCheckedAt", place.getOperatingStatusCheckedAt());
        return state;
    }

    private Map<String, Object> operatingScheduleState(MapPlace place) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("placeId", place.getId());
        state.put("regularHours", regularHours(place));
        state.put("operatingExceptions", operatingExceptions(place));
        return state;
    }

    private void validateOperatingScheduleRequest(AdminMapPlaceOperatingScheduleUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.reason())) {
            throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }

        Set<AdminMapPlaceRegularOperatingHourRequest> regularHours = request.regularHours() == null
                ? Set.of()
                : request.regularHours();
        Set<AdminMapPlaceOperatingExceptionRequest> exceptions = request.exceptions() == null
                ? Set.of()
                : request.exceptions();

        validateRegularOperatingHours(regularHours);
        validateOperatingExceptions(exceptions);
    }

    private void validateRegularOperatingHours(Set<AdminMapPlaceRegularOperatingHourRequest> regularHours) {
        Map<DayOfWeek, List<TimeSegment>> segmentsByDay = new EnumMap<>(DayOfWeek.class);
        Set<PlaceRegularOperatingHour> distinctHours = new HashSet<>();

        for (AdminMapPlaceRegularOperatingHourRequest hour : regularHours) {
            if (hour == null || hour.dayOfWeek() == null) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            validateTimeRange(hour.opensAt(), hour.closesAt());

            PlaceRegularOperatingHour regularOperatingHour = PlaceRegularOperatingHour.of(
                    hour.dayOfWeek(),
                    hour.opensAt(),
                    hour.closesAt()
            );
            if (!distinctHours.add(regularOperatingHour)) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            addWeeklySegments(segmentsByDay, hour.dayOfWeek(), hour.opensAt(), hour.closesAt());
        }

        segmentsByDay.values().forEach(this::validateNoOverlap);
    }

    private void validateOperatingExceptions(Set<AdminMapPlaceOperatingExceptionRequest> exceptions) {
        Set<LocalDate> dates = new HashSet<>();
        Map<LocalDate, List<TimeSegment>> segmentsByDate = new LinkedHashMap<>();
        for (AdminMapPlaceOperatingExceptionRequest exception : exceptions) {
            if (exception == null || exception.date() == null || !dates.add(exception.date())) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }

            Set<AdminMapPlaceOperatingTimeRangeRequest> hours = exception.hours() == null
                    ? Set.of()
                    : exception.hours();
            if (exception.closed() && !hours.isEmpty()) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            if (!exception.closed() && hours.isEmpty()) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
            if (exception.closed()) {
                segmentsByDate.computeIfAbsent(exception.date(), ignored -> new ArrayList<>())
                        .add(new TimeSegment(0, NANOS_PER_DAY));
                continue;
            }

            Set<PlaceOperatingTimeRange> distinctHours = new HashSet<>();
            for (AdminMapPlaceOperatingTimeRangeRequest hour : hours) {
                if (hour == null) {
                    throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
                }
                validateTimeRange(hour.opensAt(), hour.closesAt());
                PlaceOperatingTimeRange timeRange = PlaceOperatingTimeRange.of(hour.opensAt(), hour.closesAt());
                if (!distinctHours.add(timeRange)) {
                    throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
                }
                addExceptionSegments(segmentsByDate, exception.date(), hour.opensAt(), hour.closesAt());
            }
        }

        segmentsByDate.values().forEach(this::validateNoOverlap);
    }

    private void validateTimeRange(LocalTime opensAt, LocalTime closesAt) {
        if (opensAt == null || closesAt == null || opensAt.equals(closesAt)) {
            throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
        }
    }

    private void addWeeklySegments(
            Map<DayOfWeek, List<TimeSegment>> segmentsByDay,
            DayOfWeek dayOfWeek,
            LocalTime opensAt,
            LocalTime closesAt
    ) {
        long startsAt = opensAt.toNanoOfDay();
        long endsAt = closesAt.toNanoOfDay();
        if (startsAt < endsAt) {
            segmentsByDay.computeIfAbsent(dayOfWeek, ignored -> new ArrayList<>())
                    .add(new TimeSegment(startsAt, endsAt));
            return;
        }

        segmentsByDay.computeIfAbsent(dayOfWeek, ignored -> new ArrayList<>())
                .add(new TimeSegment(startsAt, NANOS_PER_DAY));
        segmentsByDay.computeIfAbsent(dayOfWeek.plus(1), ignored -> new ArrayList<>())
                .add(new TimeSegment(0, endsAt));
    }

    private void addExceptionSegments(
            Map<LocalDate, List<TimeSegment>> segmentsByDate,
            LocalDate date,
            LocalTime opensAt,
            LocalTime closesAt
    ) {
        long startsAt = opensAt.toNanoOfDay();
        long endsAt = closesAt.toNanoOfDay();
        if (startsAt < endsAt) {
            segmentsByDate.computeIfAbsent(date, ignored -> new ArrayList<>())
                    .add(new TimeSegment(startsAt, endsAt));
            return;
        }

        segmentsByDate.computeIfAbsent(date, ignored -> new ArrayList<>())
                .add(new TimeSegment(startsAt, NANOS_PER_DAY));
        segmentsByDate.computeIfAbsent(date.plusDays(1), ignored -> new ArrayList<>())
                .add(new TimeSegment(0, endsAt));
    }

    private void validateNoOverlap(List<TimeSegment> segments) {
        List<TimeSegment> orderedSegments = segments.stream()
                .sorted(Comparator.comparingLong(TimeSegment::startsAt)
                        .thenComparingLong(TimeSegment::endsAt))
                .toList();
        for (int index = 1; index < orderedSegments.size(); index++) {
            TimeSegment previous = orderedSegments.get(index - 1);
            TimeSegment current = orderedSegments.get(index);
            if (current.startsAt() < previous.endsAt()) {
                throw new AdminException(AdminErrorCode.PLACE_OPERATING_SCHEDULE_INVALID_REQUEST);
            }
        }
    }

    private Set<PlaceRegularOperatingHour> toRegularOperatingHours(
            Set<AdminMapPlaceRegularOperatingHourRequest> regularHours
    ) {
        if (regularHours == null || regularHours.isEmpty()) {
            return Set.of();
        }
        Set<PlaceRegularOperatingHour> results = new HashSet<>();
        regularHours.forEach(hour -> results.add(PlaceRegularOperatingHour.of(
                hour.dayOfWeek(),
                hour.opensAt(),
                hour.closesAt()
        )));
        return results;
    }

    private List<PlaceOperatingException> toOperatingExceptions(
            MapPlace mapPlace,
            Set<AdminMapPlaceOperatingExceptionRequest> exceptions
    ) {
        if (exceptions == null || exceptions.isEmpty()) {
            return List.of();
        }
        return exceptions.stream()
                .sorted(Comparator.comparing(AdminMapPlaceOperatingExceptionRequest::date))
                .map(exception -> {
                    if (exception.closed()) {
                        return PlaceOperatingException.closed(mapPlace, exception.date());
                    }
                    Set<PlaceOperatingTimeRange> hours = new HashSet<>();
                    exception.hours().forEach(hour -> hours.add(PlaceOperatingTimeRange.of(
                            hour.opensAt(),
                            hour.closesAt()
                    )));
                    return PlaceOperatingException.customHours(mapPlace, exception.date(), hours);
                })
                .toList();
    }

    private List<PlaceRegularOperatingHourResponse> regularHours(MapPlace mapPlace) {
        return mapPlace.currentRegularOperatingHours().stream()
                .sorted(Comparator.comparing(PlaceRegularOperatingHour::getDayOfWeek)
                        .thenComparing(PlaceRegularOperatingHour::getOpensAt)
                        .thenComparing(PlaceRegularOperatingHour::getClosesAt))
                .map(hour -> new PlaceRegularOperatingHourResponse(
                        hour.getDayOfWeek(),
                        hour.getOpensAt(),
                        hour.getClosesAt()
                ))
                .toList();
    }

    private List<PlaceOperatingExceptionResponse> operatingExceptions(MapPlace mapPlace) {
        return mapPlace.currentOperatingExceptions().stream()
                .map(exception -> new PlaceOperatingExceptionResponse(
                        exception.getExceptionDate(),
                        exception.isClosed(),
                        exception.currentHours().stream()
                                .sorted(Comparator.comparing(PlaceOperatingTimeRange::getOpensAt)
                                        .thenComparing(PlaceOperatingTimeRange::getClosesAt))
                                .map(hour -> new PlaceOperatingTimeRangeResponse(
                                        hour.getOpensAt(),
                                        hour.getClosesAt()
                                ))
                                .toList()
                ))
                .toList();
    }

    private void validateRecommendationTrafficRequest(AdminPlaceRecommendationTrafficUpdateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.reason())
                || request.policies() == null
                || request.policies().isEmpty()) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
        }
        if (request.policies().stream().anyMatch(policy ->
                policy == null
                        || !StringUtils.hasText(policy.recommendationVersion())
                        || policy.trafficPercentage() == null
        )) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
        }
    }

    private void validateFallbackCycle(Map<String, PlaceRecommendationPolicyService.PolicyUpdateCommand> policyCommands) {
        for (String version : policyCommands.keySet()) {
            Set<String> visitedVersions = new HashSet<>();
            String currentVersion = version;
            while (currentVersion != null) {
                if (!visitedVersions.add(currentVersion)) {
                    throw new AdminException(AdminErrorCode.RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST);
                }
                PlaceRecommendationPolicyService.PolicyUpdateCommand command = policyCommands.get(currentVersion);
                if (command == null || command.enabled()) {
                    break;
                }
                currentVersion = command.fallbackVersion();
            }
        }
    }

    private List<AdminRecommendationPolicyChangeHistory> buildRecommendationPolicyHistories(
            Long adminUserId,
            String reason,
            List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> beforePolicies,
            List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> afterPolicies
    ) {
        Map<String, PlaceRecommendationPolicyService.RecommendationTrafficPolicy> beforePolicyMap = beforePolicies.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PlaceRecommendationPolicyService.RecommendationTrafficPolicy::version,
                        policy -> policy
                ));
        List<AdminRecommendationPolicyChangeHistory> histories = new ArrayList<>();
        LocalDateTime changedAt = LocalDateTime.now(clock);

        for (PlaceRecommendationPolicyService.RecommendationTrafficPolicy afterPolicy : afterPolicies) {
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy beforePolicy =
                    beforePolicyMap.get(afterPolicy.version());
            if (beforePolicy == null || isSameRecommendationPolicy(beforePolicy, afterPolicy)) {
                continue;
            }

            histories.add(AdminRecommendationPolicyChangeHistory.builder()
                    .recommendationVersion(afterPolicy.version())
                    .changeType(AdminRecommendationPolicyChangeType.TRAFFIC_POLICY)
                    .actorUserId(adminUserId)
                    .reason(reason)
                    .beforeState(writeRecommendationPolicyHistoryValue(toRecommendationPolicyHistoryState(beforePolicy)))
                    .afterState(writeRecommendationPolicyHistoryValue(toRecommendationPolicyHistoryState(afterPolicy)))
                    .changedAt(changedAt)
                    .build());
        }
        return histories;
    }

    private boolean isSameRecommendationPolicy(
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy beforePolicy,
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy afterPolicy
    ) {
        return beforePolicy.trafficPercentage() == afterPolicy.trafficPercentage()
                && beforePolicy.enabled() == afterPolicy.enabled()
                && java.util.Objects.equals(beforePolicy.fallbackVersion(), afterPolicy.fallbackVersion());
    }

    private Map<String, Object> toRecommendationPolicyHistoryState(
            PlaceRecommendationPolicyService.RecommendationTrafficPolicy policy
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("recommendationVersion", policy.version());
        state.put("stage", policy.stage().name());
        state.put("trafficPercentage", policy.trafficPercentage());
        state.put("enabled", policy.enabled());
        state.put("fallbackVersion", policy.fallbackVersion());
        return state;
    }

    private Map<String, Object> toTrafficAuditState(
            List<PlaceRecommendationPolicyService.RecommendationTrafficPolicy> policies
    ) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("defaultVersion", placeRecommendationPolicyService.getDefaultVersion());
        state.put("policies", policies.stream()
                .map(policy -> {
                    Map<String, Object> policyState = new LinkedHashMap<>();
                    policyState.put("recommendationVersion", policy.version());
                    policyState.put("stage", policy.stage().name());
                    policyState.put("trafficPercentage", policy.trafficPercentage());
                    policyState.put("enabled", policy.enabled());
                    policyState.put("fallbackVersion", policy.fallbackVersion());
                    return policyState;
                })
                .toList());
        return state;
    }

    private void transferKakaoPlaceIdIfNeeded(MapPlace sourcePlace, MapPlace targetPlace) {
        if (StringUtils.hasText(targetPlace.getKakaoPlaceId()) || !StringUtils.hasText(sourcePlace.getKakaoPlaceId())) {
            return;
        }

        String sourceKakaoPlaceId = sourcePlace.getKakaoPlaceId();
        sourcePlace.updateKakaoPlaceId(null);
        mapPlaceRepository.flush();
        targetPlace.updateKakaoPlaceId(sourceKakaoPlaceId);
    }

    private PlaceSnapshot placeSnapshot(MapPlace place) {
        return new PlaceSnapshot(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getRoadAddress(),
                place.getJibunAddress(),
                place.getPostalCode(),
                place.getGeocodingSource(),
                place.getOperatingStatus(),
                place.getOperatingStatusCheckedAt(),
                place.getCategory(),
                place.getImageUrl(),
                place.getKakaoPlaceId(),
                place.getLatitude(),
                place.getLongitude(),
                place.getUserId(),
                place.getRegistrant(),
                place.currentPhotoCount(),
                place.getEnglishName(),
                place.getTouristSummary(),
                normalizeTouristCategories(place.currentTouristCategories()),
                regularOperatingHourSnapshots(place),
                operatingExceptionSnapshots(place)
        );
    }

    private void restoreImages(MapPlace restoredSourcePlace, List<Long> movedImageIds) {
        mapImageRepository.findAllById(movedImageIds).forEach(image -> image.reassignPlace(restoredSourcePlace));
    }

    private MapPlace insertRestoredPlace(PlaceSnapshot sourceSnapshot) {
        jdbcTemplate.update(
                """
                INSERT INTO map_place (
                    map_place_id, place_name, address, road_address, jibun_address, postal_code, geocoding_source,
                    operating_status, operating_status_checked_at, category, image_url, kakao_place_id,
                    latitude, longitude, user_id, registrant, photo_count, english_name, tourist_summary
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sourceSnapshot.id(),
                sourceSnapshot.name(),
                sourceSnapshot.address(),
                sourceSnapshot.roadAddress(),
                sourceSnapshot.jibunAddress(),
                sourceSnapshot.postalCode(),
                (sourceSnapshot.geocodingSource() == null
                        ? GeocodingSource.LEGACY
                        : sourceSnapshot.geocodingSource()).name(),
                (sourceSnapshot.operatingStatus() == null
                        ? PlaceOperatingStatus.OPERATING
                        : sourceSnapshot.operatingStatus()).name(),
                sourceSnapshot.operatingStatusCheckedAt(),
                sourceSnapshot.category(),
                sourceSnapshot.imageUrl(),
                sourceSnapshot.kakaoPlaceId(),
                sourceSnapshot.latitude(),
                sourceSnapshot.longitude(),
                sourceSnapshot.userId(),
                sourceSnapshot.registrant(),
                sourceSnapshot.photoCount() == null ? 0L : sourceSnapshot.photoCount(),
                sourceSnapshot.englishName(),
                sourceSnapshot.touristSummary()
        );
        MapPlace restoredSourcePlace = mapPlaceRepository.findById(sourceSnapshot.id())
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED));
        restoredSourcePlace.updateCoordinates(
                sourceSnapshot.latitude(),
                sourceSnapshot.longitude(),
                toPoint(sourceSnapshot.latitude(), sourceSnapshot.longitude())
        );
        restoredSourcePlace.updateTouristInformation(
                sourceSnapshot.englishName(),
                sourceSnapshot.touristSummary(),
                normalizeTouristCategories(sourceSnapshot.touristCategories())
        );
        restoredSourcePlace.replaceOperatingSchedule(
                regularOperatingHours(sourceSnapshot.regularOperatingHours()),
                operatingExceptions(restoredSourcePlace, sourceSnapshot.operatingExceptions())
        );
        return restoredSourcePlace;
    }

    private List<RegularOperatingHourSnapshot> regularOperatingHourSnapshots(MapPlace place) {
        return place.currentRegularOperatingHours().stream()
                .map(hour -> new RegularOperatingHourSnapshot(
                        hour.getDayOfWeek(),
                        hour.getOpensAt(),
                        hour.getClosesAt()
                ))
                .toList();
    }

    private List<OperatingExceptionSnapshot> operatingExceptionSnapshots(MapPlace place) {
        return place.currentOperatingExceptions().stream()
                .map(exception -> new OperatingExceptionSnapshot(
                        exception.getExceptionDate(),
                        exception.isClosed(),
                        exception.currentHours().stream()
                                .map(hour -> new OperatingTimeRangeSnapshot(hour.getOpensAt(), hour.getClosesAt()))
                                .toList()
                ))
                .toList();
    }

    private Set<PlaceRegularOperatingHour> regularOperatingHours(
            List<RegularOperatingHourSnapshot> snapshots
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Set.of();
        }
        Set<PlaceRegularOperatingHour> hours = new HashSet<>();
        snapshots.forEach(snapshot -> hours.add(PlaceRegularOperatingHour.of(
                snapshot.dayOfWeek(),
                snapshot.opensAt(),
                snapshot.closesAt()
        )));
        return hours;
    }

    private List<PlaceOperatingException> operatingExceptions(
            MapPlace mapPlace,
            List<OperatingExceptionSnapshot> snapshots
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        return snapshots.stream()
                .sorted(Comparator.comparing(OperatingExceptionSnapshot::date))
                .map(snapshot -> {
                    if (snapshot.closed()) {
                        return PlaceOperatingException.closed(mapPlace, snapshot.date());
                    }
                    Set<PlaceOperatingTimeRange> hours = new HashSet<>();
                    if (snapshot.hours() != null) {
                        snapshot.hours().forEach(hour -> hours.add(PlaceOperatingTimeRange.of(
                                hour.opensAt(),
                                hour.closesAt()
                        )));
                    }
                    return PlaceOperatingException.customHours(mapPlace, snapshot.date(), hours);
                })
                .toList();
    }

    private void restoreBookmarks(MapPlace restoredSourcePlace, String movedBookmarkIds, String deletedBookmarks) {
        mapBookmarkRepository.findAllById(readLongList(movedBookmarkIds))
                .forEach(bookmark -> bookmark.reassignPlace(restoredSourcePlace.getId()));
        List<BookmarkSnapshot> removedBookmarks = readValue(deletedBookmarks, new TypeReference<>() {
        });
        if (removedBookmarks.isEmpty()) {
            return;
        }
        List<MapBookmark> bookmarksToSave = removedBookmarks.stream()
                .map(snapshot -> MapBookmark.builder()
                        .userId(snapshot.userId())
                        .placeId(restoredSourcePlace.getId())
                        .build())
                .toList();
        mapBookmarkRepository.saveAll(bookmarksToSave);
    }

    private void restoreConversions(MapPlace restoredSourcePlace, String movedConversionIds, String deletedConversions) {
        placeRecommendationConversionRepository.findAllById(readLongList(movedConversionIds))
                .forEach(conversion -> conversion.reassignPlace(restoredSourcePlace.getId()));
        List<ConversionSnapshot> removedConversions = readValue(deletedConversions, new TypeReference<>() {
        });
        if (removedConversions.isEmpty()) {
            return;
        }
        List<PlaceRecommendationConversion> conversionsToSave = removedConversions.stream()
                .map(snapshot -> snapshot.toEntity(restoredSourcePlace.getId()))
                .toList();
        placeRecommendationConversionRepository.saveAll(conversionsToSave);
    }

    private void restoreClicks(Long sourcePlaceId, List<Long> movedClickIds) {
        if (movedClickIds.isEmpty()) {
            return;
        }
        placeRecommendationClickRepository.updatePlaceIdForIds(sourcePlaceId, movedClickIds);
    }

    private void restoreExposures(Long sourcePlaceId, List<Long> movedExposureIds) {
        if (movedExposureIds.isEmpty()) {
            return;
        }
        placeRecommendationExposureRepository.updatePlaceIdForIds(sourcePlaceId, movedExposureIds);
    }

    private void restoreFeatureLogs(Long sourcePlaceId, List<Long> movedFeatureLogIds) {
        if (movedFeatureLogIds.isEmpty()) {
            return;
        }
        placeRecommendationFeatureLogRepository.updatePlaceIdForIds(sourcePlaceId, movedFeatureLogIds);
    }

    private void restoreKakaoPlaceIds(
            MapPlace restoredSourcePlace,
            MapPlace targetPlace,
            PlaceSnapshot sourceSnapshot,
            PlaceSnapshot targetSnapshot
    ) {
        restoredSourcePlace.updateKakaoPlaceId(sourceSnapshot.kakaoPlaceId());
        targetPlace.updateKakaoPlaceId(targetSnapshot.kakaoPlaceId());
    }

    private void prepareTargetBeforeRestore(
            MapPlace targetPlace,
            PlaceSnapshot sourceSnapshot,
            PlaceSnapshot targetSnapshot
    ) {
        if (sourceSnapshot.kakaoPlaceId() == null || targetSnapshot.kakaoPlaceId() != null) {
            return;
        }
        if (sourceSnapshot.kakaoPlaceId().equals(targetPlace.getKakaoPlaceId())) {
            targetPlace.updateKakaoPlaceId(null);
            mapPlaceRepository.flush();
        }
    }

    private List<Long> readLongList(String value) {
        return readValue(value, new TypeReference<>() {
        });
    }

    private <T> T readValue(String value, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED);
        }
    }

    private String writeRecommendationPolicyHistoryValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_POLICY_HISTORY_WRITE_FAILED, exception);
        }
    }

    private final class MergeExecutionContext {
        private final PlaceSnapshot sourcePlaceSnapshot;
        private final PlaceSnapshot targetPlaceSnapshot;
        private final List<Long> movedImageIds = new ArrayList<>();
        private final List<Long> movedBookmarkIds = new ArrayList<>();
        private final List<BookmarkSnapshot> deletedBookmarks = new ArrayList<>();
        private final List<Long> movedConversionIds = new ArrayList<>();
        private final List<ConversionSnapshot> deletedConversions = new ArrayList<>();
        private final List<Long> movedClickIds = new ArrayList<>();
        private final List<Long> movedExposureIds = new ArrayList<>();
        private final List<Long> movedFeatureLogIds = new ArrayList<>();

        private MergeExecutionContext(PlaceSnapshot sourcePlaceSnapshot, PlaceSnapshot targetPlaceSnapshot) {
            this.sourcePlaceSnapshot = sourcePlaceSnapshot;
            this.targetPlaceSnapshot = targetPlaceSnapshot;
        }

        private List<Long> movedImageIds() { return movedImageIds; }
        private List<Long> movedBookmarkIds() { return movedBookmarkIds; }
        private List<BookmarkSnapshot> deletedBookmarks() { return deletedBookmarks; }
        private List<Long> movedConversionIds() { return movedConversionIds; }
        private List<ConversionSnapshot> deletedConversions() { return deletedConversions; }
        private List<Long> movedClickIds() { return movedClickIds; }
        private List<Long> movedExposureIds() { return movedExposureIds; }
        private List<Long> movedFeatureLogIds() { return movedFeatureLogIds; }

        private AdminPlaceMergeHistory toHistory(Long adminUserId) {
            return AdminPlaceMergeHistory.builder()
                    .sourcePlaceId(sourcePlaceSnapshot.id())
                    .targetPlaceId(targetPlaceSnapshot.id())
                    .adminUserId(adminUserId)
                    .sourcePlaceSnapshot(writeValue(sourcePlaceSnapshot))
                    .targetPlaceSnapshot(writeValue(targetPlaceSnapshot))
                    .movedImageIds(writeValue(movedImageIds))
                    .movedBookmarkIds(writeValue(movedBookmarkIds))
                    .deletedBookmarks(writeValue(deletedBookmarks))
                    .movedConversionIds(writeValue(movedConversionIds))
                    .deletedConversions(writeValue(deletedConversions))
                    .movedClickIds(writeValue(movedClickIds))
                    .movedExposureIds(writeValue(movedExposureIds))
                    .movedFeatureLogIds(writeValue(movedFeatureLogIds))
                    .mergedAt(now())
                    .build();
        }
    }

    private record PlaceSnapshot(
            Long id,
            String name,
            String address,
            String roadAddress,
            String jibunAddress,
            String postalCode,
            GeocodingSource geocodingSource,
            PlaceOperatingStatus operatingStatus,
            LocalDateTime operatingStatusCheckedAt,
            String category,
            String imageUrl,
            String kakaoPlaceId,
            Double latitude,
            Double longitude,
            Long userId,
            String registrant,
            Long photoCount,
            String englishName,
            String touristSummary,
            Set<TouristCategory> touristCategories,
            List<RegularOperatingHourSnapshot> regularOperatingHours,
            List<OperatingExceptionSnapshot> operatingExceptions
    ) {
    }

    private record RegularOperatingHourSnapshot(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    }

    private record OperatingExceptionSnapshot(
            LocalDate date,
            boolean closed,
            List<OperatingTimeRangeSnapshot> hours
    ) {
    }

    private record OperatingTimeRangeSnapshot(LocalTime opensAt, LocalTime closesAt) {
    }

    private record TimeSegment(long startsAt, long endsAt) {
    }

    private record BookmarkSnapshot(Long userId) {
    }

    private record ConversionSnapshot(
            Long placeRecommendationClickId,
            Long userId,
            PlaceRecommendationConversionType conversionType,
            String recommendationVersion
    ) {
        private static ConversionSnapshot from(PlaceRecommendationConversion conversion) {
            return new ConversionSnapshot(
                    conversion.getPlaceRecommendationClickId(),
                    conversion.getUserId(),
                    conversion.getConversionType(),
                    conversion.getRecommendationVersion()
            );
        }

        private PlaceRecommendationConversion toEntity(Long placeId) {
            return PlaceRecommendationConversion.builder()
                    .placeRecommendationClickId(placeRecommendationClickId)
                    .placeId(placeId)
                    .userId(userId)
                    .conversionType(conversionType)
                    .recommendationVersion(recommendationVersion)
                    .build();
        }
    }
}
