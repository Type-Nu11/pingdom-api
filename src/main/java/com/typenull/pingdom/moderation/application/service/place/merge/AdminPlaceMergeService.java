package com.typenull.pingdom.moderation.application.service.place.merge;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.place.quality.AdminPlaceServiceSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryItem;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminPlaceMergeRestoreResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.place.AdminPlaceMergeHistory;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateCandidate;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminPlaceMergeHistoryRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.PlaceDuplicateCandidateRepository;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import com.typenull.pingdom.verification.infrastructure.ScoutFieldReportRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 장소 병합·이력 조회·복구 구현을 담당한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlaceMergeService {
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceEventRepository placeEventRepository;
    private final LocationCheckInRepository locationCheckInRepository;
    private final ScoutFieldReportRepository scoutFieldReportRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;
    private final PlaceRecommendationSnapshotResyncService placeRecommendationSnapshotResyncService;
    private final AdminPlaceDuplicateResolver adminPlaceDuplicateResolver;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminPlaceMergeHistoryRepository adminPlaceMergeHistoryRepository;
    private final PlaceDuplicateCandidateRepository placeDuplicateCandidateRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Transactional
    /** 원본 장소의 병합 가능성을 검증한 뒤 대상 장소로 데이터를 이동하고 이력을 남깁니다. */
    public AdminMapPlaceMergeResponse mergePlaces(Long adminUserId, AdminMapPlaceMergeRequest request) {
        validateMergeRequest(request);

        PlaceDuplicateCandidate duplicateCandidate = null;
        if (request.candidateId() != null) {
            duplicateCandidate = placeDuplicateCandidateRepository.findByIdForUpdate(request.candidateId())
                    .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_DUPLICATE_CANDIDATE_NOT_FOUND));
            if (duplicateCandidate.getStatus() != PlaceDuplicateDecisionStatus.CONFIRMED
                    || !candidateMatchesRequest(duplicateCandidate, request)) {
                throw new AdminException(AdminErrorCode.PLACE_MERGE_NOT_ALLOWED);
            }
        }

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
        if (locationCheckInRepository.existsByPlaceId(sourcePlace.getId())) {
            throw new AdminException(AdminErrorCode.PLACE_CHECK_IN_CONNECTED);
        }
        if (scoutFieldReportRepository.existsByPlaceId(sourcePlace.getId())) {
            throw new AdminException(AdminErrorCode.PLACE_SCOUT_FIELD_REPORT_CONNECTED);
        }
        rejectUnsupportedRelatedData(sourcePlace.getId());
        if (duplicateCandidate == null && !adminPlaceDuplicateResolver.areDuplicates(sourcePlace, targetPlace)) {
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
        mapBookmarkTrendEventRepository.reassignPlace(sourcePlace.getId(), targetPlace.getId());
        ConversionMergeResult conversionMergeResult = reassignConversions(sourcePlace, targetPlace, mergeExecutionContext);
        int movedClickCount = reassignClicks(sourcePlace, targetPlace, mergeExecutionContext);
        int movedExposureCount = reassignExposures(sourcePlace, targetPlace, mergeExecutionContext);
        int movedFeatureLogCount = reassignFeatureLogs(sourcePlace, targetPlace, mergeExecutionContext);

        targetPlace.replacePhotoCount(countActiveImages(targetPlace.getId()));
        mapPlaceRepository.delete(sourcePlace);
        AdminPlaceMergeHistory mergeHistory = adminPlaceMergeHistoryRepository.save(
                mergeExecutionContext.toHistory(adminUserId)
        );
        if (duplicateCandidate != null) {
            duplicateCandidate.markMerged(mergeHistory.getId(), now());
        }
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
        mapBookmarkTrendEventRepository.restoreOriginalPlace(
                history.getSourcePlaceId(),
                targetPlace.getId(),
                restoredSourcePlace.getId()
        );
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

    private void validateMergeRequest(AdminMapPlaceMergeRequest request) {
        if (request == null || request.sourcePlaceId() == null || request.targetPlaceId() == null) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
        if (request.sourcePlaceId().equals(request.targetPlaceId())) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
    }

    private void rejectUnsupportedRelatedData(Long sourcePlaceId) {
        Set<String> supportedTables = Set.of(
                "map_image",
                "map_bookmark",
                "map_bookmark_trend_event",
                "place_recommendation_click",
                "place_recommendation_exposure",
                "place_recommendation_conversion",
                "place_recommendation_feature_log"
        );
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                SELECT tc.table_name, kcu.column_name
                  FROM information_schema.table_constraints tc
                  JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                   AND tc.table_schema = kcu.table_schema
                  JOIN information_schema.constraint_column_usage ccu
                    ON tc.constraint_name = ccu.constraint_name
                   AND tc.table_schema = ccu.table_schema
                 WHERE tc.constraint_type = 'FOREIGN KEY'
                   AND tc.table_schema = current_schema()
                   AND ccu.table_name = 'map_place'
                """);
        for (Map<String, Object> foreignKey : foreignKeys) {
            String tableName = String.valueOf(foreignKey.get("table_name"));
            if (supportedTables.contains(tableName) || "map_place".equals(tableName)) {
                continue;
            }
            String columnName = String.valueOf(foreignKey.get("column_name"));
            String tableIdentifier = quoteIdentifier(tableName);
            String columnIdentifier = quoteIdentifier(columnName);
            Long relatedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableIdentifier + " WHERE " + columnIdentifier + " = ?",
                    Long.class,
                    sourcePlaceId
            );
            if (relatedCount != null && relatedCount > 0) {
                throw new AdminException(AdminErrorCode.PLACE_MERGE_RELATED_DATA_CONNECTED);
            }
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private boolean candidateMatchesRequest(
            PlaceDuplicateCandidate candidate,
            AdminMapPlaceMergeRequest request
    ) {
        return Set.of(candidate.getLeftPlaceId(), candidate.getRightPlaceId())
                .equals(Set.of(request.sourcePlaceId(), request.targetPlaceId()));
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

    private Map<String, Object> placeMergeBeforeState(MapPlace sourcePlace, MapPlace targetPlace) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("sourcePlace", AdminPlaceServiceSupport.placeState(sourcePlace));
        state.put("targetPlace", AdminPlaceServiceSupport.placeState(targetPlace));
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
        state.put("targetPlace", AdminPlaceServiceSupport.placeState(targetPlace));
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
                AdminPlaceServiceSupport.normalizeTouristCategories(place.currentTouristCategories()),
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
                    latitude, longitude, user_id, registrant, photo_count, english_name, tourist_summary,
                    discovery_status, primary_information_source, information_verification_status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                sourceSnapshot.touristSummary(),
                PlaceDiscoveryStatus.VISIBLE.name(),
                PlaceInformationSourceType.LEGACY.name(),
                PlaceInformationVerificationStatus.UNVERIFIED.name(),
                now()
        );
        MapPlace restoredSourcePlace = mapPlaceRepository.findById(sourceSnapshot.id())
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_MERGE_RESTORE_NOT_ALLOWED));
        restoredSourcePlace.updateCoordinates(
                sourceSnapshot.latitude(),
                sourceSnapshot.longitude(),
                AdminPlaceServiceSupport.toPoint(sourceSnapshot.latitude(), sourceSnapshot.longitude())
        );
        restoredSourcePlace.updateTouristInformation(
                sourceSnapshot.englishName(),
                sourceSnapshot.touristSummary(),
                AdminPlaceServiceSupport.normalizeTouristCategories(sourceSnapshot.touristCategories())
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
