package com.typenull.pingdom.moderation.application.service.place.quality;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.api.dto.place.quality.basic.AdminMapPlaceBasicInformationUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.basic.AdminMapPlaceBasicInformationUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.coordinate.AdminMapPlaceCoordinateUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.discovery.AdminMapPlaceDiscoveryStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.discovery.AdminMapPlaceDiscoveryStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.geocoding.AdminMapPlaceGeocodingUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceCreateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceItem;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceReviewRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.information.AdminPlaceInformationEvidenceUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.kakao.AdminMapPlaceKakaoPlaceIdUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingStatusUpdateResponse;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateResponse;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.category.PlaceCategory;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.application.service.localhot.PlaceAdministrativeRegionService;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidence;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationEvidenceRepository;
import com.typenull.pingdom.place.outbox.PlaceRecommendationResyncOutboxPayload;
import com.typenull.pingdom.place.outbox.PlaceInformationEvidenceSubmittedOutboxPayload;
import com.typenull.pingdom.place.outbox.PlaceInformationVerificationUpdatedOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.observability.PlaceDiscoveryMetrics;
import com.typenull.pingdom.shared.observability.PlaceInformationMetrics;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 장소 좌표·식별자·관광정보·운영품질·정보 근거 변경을 담당. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlaceQualityService {
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceInformationEvidenceRepository placeInformationEvidenceRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;
    private final PlaceDiscoveryMetrics placeDiscoveryMetrics;
    private final PlaceInformationMetrics placeInformationMetrics;
    private final PlaceAdministrativeRegionService placeAdministrativeRegionService;

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * 장소를 잠가 이름의 양끝 공백을 제거한 값과 카테고리를 갱신하고 변경 전후 감사 기록을 함께 저장.
     * 장소가 없으면 PLACE_NOT_FOUND이며 응답의 수정 시각은 저장한 감사 기록의 생성 시각을 사용.
     */
    @Transactional
    public AdminMapPlaceBasicInformationUpdateResponse updatePlaceBasicInformation(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceBasicInformationUpdateRequest request
    ) {
        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        String normalizedName = AdminPlaceServiceSupport.trimToNull(request.name());
        PlaceCategory category = request.category();
        Map<String, Object> beforeState = AdminPlaceServiceSupport.basicInformationState(mapPlace);

        mapPlace.updateBasicInformation(normalizedName, category.name());

        Map<String, Object> afterState = AdminPlaceServiceSupport.basicInformationState(mapPlace);
        AdminAuditLog auditLog = adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_BASIC_INFORMATION_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );

        log.info(
                "Admin updated place basic information. adminUserId={}, placeId={}, name={}, category={}",
                adminUserId,
                placeId,
                mapPlace.getName(),
                category
        );

        return new AdminMapPlaceBasicInformationUpdateResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                category,
                auditLog.getCreatedAt(),
                "장소 기본 정보를 수정했습니다."
        );
    }

    /**
     * 좌표 필수값을 확인하고 장소 잠금 아래 ADMIN 출처 좌표·행정구역을 갱신한 뒤 추천 재동기화 예약.
     * 좌표 범위는 API 요청 검증에 의존하며 중복 장소 판별·별도 감사 기록 저장은 처리 범위에서 제외.
     */
    @Transactional
    public AdminMapPlaceCoordinateUpdateResponse updatePlaceCoordinates(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceCoordinateUpdateRequest request
    ) {
        if (request == null || request.latitude() == null || request.longitude() == null) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
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
                AdminPlaceServiceSupport.toPoint(request.latitude(), request.longitude()),
                GeocodingSource.ADMIN
        );
        placeAdministrativeRegionService.synchronizeIfConfigured(mapPlace);

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

    /**
     * 대표 주소는 도로명, 지번, 요청 대표 주소 순으로 선택. 좌표와 ADMIN 출처를 반영하고
     * 행정구역 동기화·감사 기록·추천 재동기화 outbox를 현재 트랜잭션에 연결.
     */
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
        Map<String, Object> beforeState = AdminPlaceServiceSupport.geocodingState(mapPlace);
        String normalizedRoadAddress = AdminPlaceServiceSupport.trimToNull(request.roadAddress());
        String normalizedJibunAddress = AdminPlaceServiceSupport.trimToNull(request.jibunAddress());
        String representativeAddress = normalizedRoadAddress != null
                ? normalizedRoadAddress
                : normalizedJibunAddress != null ? normalizedJibunAddress : request.address().trim();

        mapPlace.updateGeocoding(
                representativeAddress,
                normalizedRoadAddress,
                normalizedJibunAddress,
                AdminPlaceServiceSupport.trimToNull(request.postalCode()),
                request.latitude(),
                request.longitude(),
                AdminPlaceServiceSupport.toPoint(request.latitude(), request.longitude()),
                GeocodingSource.ADMIN
        );
        placeAdministrativeRegionService.synchronizeIfConfigured(mapPlace);
        Map<String, Object> afterState = AdminPlaceServiceSupport.geocodingState(mapPlace);

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

    /**
     * 공백·null 요청은 기존 Kakao ID 제거. 다른 장소의 현재 ID를 조회해 충돌 확인.
     * ID 조회 자체에는 직렬화 잠금이 없어 동시 요청 간 경쟁 가능.
     */
    @Transactional
    public AdminMapPlaceKakaoPlaceIdUpdateResponse updatePlaceKakaoPlaceId(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceKakaoPlaceIdUpdateRequest request
    ) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        String normalizedKakaoPlaceId = AdminPlaceServiceSupport.trimToNull(request == null ? null : request.kakaoPlaceId());
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

    /**
     * 장소를 잠가 영문명·관광 설명·관광 카테고리를 전체 교체하고 감사 기록을 같은 트랜잭션에 저장.
     * 공백 문자열은 null, null 카테고리 집합은 빈 집합으로 처리하므로 생략한 기존 값도 제거. 장소가 없으면 PLACE_NOT_FOUND.
     */
    @Transactional
    public AdminMapPlaceTouristInfoUpdateResponse updatePlaceTouristInfo(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceTouristInfoUpdateRequest request
    ) {
        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        String normalizedEnglishName = AdminPlaceServiceSupport.trimToNull(request.englishName());
        String normalizedTouristSummary = AdminPlaceServiceSupport.trimToNull(request.touristSummary());
        Set<TouristCategory> normalizedTouristCategories = AdminPlaceServiceSupport.normalizeTouristCategories(request.touristCategories());
        Map<String, Object> beforeState = AdminPlaceServiceSupport.touristInfoState(mapPlace);
        int beforeTouristCategoryCount = mapPlace.currentTouristCategories().size();

        mapPlace.updateTouristInformation(
                normalizedEnglishName,
                normalizedTouristSummary,
                normalizedTouristCategories
        );

        Map<String, Object> afterState = AdminPlaceServiceSupport.touristInfoState(mapPlace);
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

    /**
     * 운영 상태와 공백이 아닌 사유를 확인한 뒤 장소를 잠가 상태·확인 시각과 감사 기록을 함께 갱신.
     * 잘못된 요청이나 없는 장소는 거절하며 상태가 같아도 확인 시각을 현재 Clock 시각으로 변경.
     */
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
        Map<String, Object> beforeState = AdminPlaceServiceSupport.operatingStatusState(mapPlace);
        LocalDateTime checkedAt = now();

        mapPlace.updateOperatingStatus(request.operatingStatus(), checkedAt);

        Map<String, Object> afterState = AdminPlaceServiceSupport.operatingStatusState(mapPlace);
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

    /**
     * 노출 상태와 공백이 아닌 사유를 확인한 뒤 장소를 잠가 탐색 노출 여부와 감사 기록을 갱신.
     * 잘못된 요청이나 없는 장소는 거절하며 변경 지표 기록은 트랜잭션 커밋 전에 호출.
     */
    @Transactional
    public AdminMapPlaceDiscoveryStatusUpdateResponse updatePlaceDiscoveryStatus(
            Long adminUserId,
            Long placeId,
            AdminMapPlaceDiscoveryStatusUpdateRequest request
    ) {
        if (request == null || request.discoveryStatus() == null || !StringUtils.hasText(request.reason())) {
            throw new AdminException(AdminErrorCode.PLACE_DISCOVERY_STATUS_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        Map<String, Object> beforeState = AdminPlaceServiceSupport.discoveryStatusState(mapPlace);
        PlaceDiscoveryStatus beforeStatus = mapPlace.getDiscoveryStatus();

        mapPlace.updateDiscoveryStatus(request.discoveryStatus());

        Map<String, Object> afterState = AdminPlaceServiceSupport.discoveryStatusState(mapPlace);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_DISCOVERY_STATUS_UPDATED,
                AdminAuditTargetType.PLACE,
                placeId,
                request.reason().trim(),
                beforeState,
                afterState
        );
        placeDiscoveryMetrics.recordStatusUpdate(beforeStatus, mapPlace.getDiscoveryStatus());

        log.info(
                "Admin updated place discovery status. adminUserId={}, placeId={}, beforeStatus={}, afterStatus={}",
                adminUserId,
                placeId,
                beforeStatus,
                mapPlace.getDiscoveryStatus()
        );

        return new AdminMapPlaceDiscoveryStatusUpdateResponse(
                mapPlace.getId(),
                mapPlace.getDiscoveryStatus(),
                "장소 탐색 노출 상태를 수정했습니다."
        );
    }

    /**
     * 장소 존재를 확인한 뒤 검증 근거를 갱신 시각·ID 내림차순으로 반환.
     * 장소가 없으면 PLACE_NOT_FOUND이며 장소에 근거가 없으면 빈 목록을 반환.
     */
    @Transactional(readOnly = true)
    public AdminPlaceInformationEvidenceResponse getPlaceInformationEvidence(Long placeId) {
        if (!mapPlaceRepository.existsById(placeId)) {
            throw new AdminException(AdminErrorCode.PLACE_NOT_FOUND);
        }
        List<AdminPlaceInformationEvidenceItem> evidences = placeInformationEvidenceRepository
                .findAllByPlace_IdOrderByUpdatedAtDescIdDesc(placeId)
                .stream()
                .map(AdminPlaceInformationEvidenceItem::from)
                .toList();
        return new AdminPlaceInformationEvidenceResponse(placeId, evidences);
    }

    /**
     * 증빙을 저장하고 장소의 대표 정보 출처·검증 상태를 신규 증빙 상태로 교체.
     * 제출자 ID 생략 시 관리자 ID 사용. MERCHANT_OWNER 출처는 소유주 제출 상태로 표시.
     * 실제 소유권 조회를 통한 출처 입증과 기존 증빙 간 우선순위 비교는 검증 범위에서 제외.
     */
    @Transactional
    public AdminPlaceInformationEvidenceUpdateResponse createPlaceInformationEvidence(
            Long adminUserId,
            Long placeId,
            AdminPlaceInformationEvidenceCreateRequest request
    ) {
        if (request == null || request.sourceType() == null || request.evidenceType() == null) {
            throw new AdminException(AdminErrorCode.PLACE_INFORMATION_VERIFICATION_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        Long submittedByUserId = request.submittedByUserId() == null ? adminUserId : request.submittedByUserId();
        PlaceInformationEvidence evidence;
        try {
            evidence = PlaceInformationEvidence.submit(
                    mapPlace,
                    request.sourceType(),
                    request.evidenceType(),
                    request.externalReference(),
                    request.referenceUrl(),
                    request.description(),
                    submittedByUserId,
                    now
            );
        } catch (IllegalArgumentException exception) {
            throw new AdminException(AdminErrorCode.PLACE_INFORMATION_VERIFICATION_INVALID_REQUEST);
        }
        if (request.sourceType() == PlaceInformationSourceType.MERCHANT_OWNER) {
            evidence.markOwnerSubmitted(now);
        }
        PlaceInformationEvidence savedEvidence = placeInformationEvidenceRepository.save(evidence);
        mapPlace.updateInformationVerification(
                savedEvidence.getSourceType(),
                savedEvidence.getVerificationStatus(),
                null,
                null,
                now
        );

        Map<String, Object> afterState = AdminPlaceServiceSupport.informationEvidenceState(savedEvidence);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_INFORMATION_EVIDENCE_UPDATED,
                AdminAuditTargetType.PLACE_INFORMATION_EVIDENCE,
                savedEvidence.getId(),
                "PLACE_INFORMATION_EVIDENCE_CREATED",
                Map.of(),
                afterState
        );
        placeInformationMetrics.recordEvidenceSubmitted(savedEvidence.getSourceType());
        publishEvidenceSubmittedEvent(mapPlace.getId(), savedEvidence);

        return new AdminPlaceInformationEvidenceUpdateResponse(
                AdminPlaceInformationEvidenceItem.from(savedEvidence),
                "장소 정보 증빙을 등록했습니다."
        );
    }

    /**
     * 장소를 먼저, 해당 장소의 증빙을 다음으로 잠그고 ADMIN_VERIFIED 또는 REJECTED만 적용.
     * 개별 증빙 심사 결과를 장소 대표 검증 상태에도 반영하고 감사 기록·상태 변경 outbox를 저장.
     */
    @Transactional
    public AdminPlaceInformationEvidenceUpdateResponse reviewPlaceInformationEvidence(
            Long adminUserId,
            Long placeId,
            Long evidenceId,
            AdminPlaceInformationEvidenceReviewRequest request
    ) {
        if (request == null || request.verificationStatus() == null) {
            throw new AdminException(AdminErrorCode.PLACE_INFORMATION_VERIFICATION_INVALID_REQUEST);
        }
        if (request.verificationStatus() != PlaceInformationVerificationStatus.ADMIN_VERIFIED
                && request.verificationStatus() != PlaceInformationVerificationStatus.REJECTED) {
            throw new AdminException(AdminErrorCode.PLACE_INFORMATION_VERIFICATION_INVALID_REQUEST);
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        PlaceInformationEvidence evidence = placeInformationEvidenceRepository.findByIdAndPlace_IdForUpdate(evidenceId, placeId)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_INFORMATION_EVIDENCE_NOT_FOUND));
        Map<String, Object> beforeState = AdminPlaceServiceSupport.informationEvidenceState(evidence);
        PlaceInformationVerificationStatus beforeStatus = evidence.getVerificationStatus();
        LocalDateTime now = LocalDateTime.now(clock);

        try {
            if (request.verificationStatus() == PlaceInformationVerificationStatus.ADMIN_VERIFIED) {
                evidence.verifyByAdmin(adminUserId, request.reviewReason(), now);
                mapPlace.updateInformationVerification(evidence.getSourceType(), evidence.getVerificationStatus(), adminUserId, now, now);
            } else {
                evidence.reject(adminUserId, request.reviewReason(), now);
                mapPlace.updateInformationVerification(evidence.getSourceType(), evidence.getVerificationStatus(), null, null, now);
            }
        } catch (IllegalArgumentException exception) {
            throw new AdminException(AdminErrorCode.PLACE_INFORMATION_VERIFICATION_INVALID_REQUEST);
        }

        Map<String, Object> afterState = AdminPlaceServiceSupport.informationEvidenceState(evidence);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.PLACE_INFORMATION_VERIFICATION_UPDATED,
                AdminAuditTargetType.PLACE_INFORMATION_EVIDENCE,
                evidence.getId(),
                request.reviewReason(),
                beforeState,
                afterState
        );
        placeInformationMetrics.recordVerificationStatusUpdate(beforeStatus, evidence.getVerificationStatus());
        publishVerificationUpdatedEvent(mapPlace.getId(), evidence, beforeStatus);

        return new AdminPlaceInformationEvidenceUpdateResponse(
                AdminPlaceInformationEvidenceItem.from(evidence),
                "장소 정보 증빙 검토 상태를 수정했습니다."
        );
    }

    private void requestRecommendationResync(MapPlace place, String reason) {
        String deduplicationKey = "PLACE_RECOMMENDATION_RESYNC:%d:%s".formatted(
                place.getId(),
                UUID.randomUUID()
        );
        outboxEventPublisher.publishCoalesced(
                deduplicationKey,
                OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED,
                new PlaceRecommendationResyncOutboxPayload(place.getId(), reason),
                "MAP_PLACE",
                String.valueOf(place.getId())
        );
    }

    private void publishEvidenceSubmittedEvent(Long placeId, PlaceInformationEvidence evidence) {
        outboxEventPublisher.publish(
                "place-information-evidence-submitted:" + evidence.getId(),
                OutboxEventType.PLACE_INFORMATION_EVIDENCE_SUBMITTED,
                new PlaceInformationEvidenceSubmittedOutboxPayload(
                        placeId,
                        evidence.getId(),
                        evidence.getSourceType(),
                        evidence.getEvidenceType(),
                        evidence.getSubmittedByUserId(),
                        evidence.getSubmittedAt()
                ),
                "PLACE",
                String.valueOf(placeId)
        );
    }

    private void publishVerificationUpdatedEvent(
            Long placeId,
            PlaceInformationEvidence evidence,
            PlaceInformationVerificationStatus beforeStatus
    ) {
        outboxEventPublisher.publish(
                "place-information-verification-updated:%d:%s:%s".formatted(
                        evidence.getId(),
                        evidence.getVerificationStatus(),
                        evidence.getUpdatedAt()
                ),
                OutboxEventType.PLACE_INFORMATION_VERIFICATION_UPDATED,
                new PlaceInformationVerificationUpdatedOutboxPayload(
                        placeId,
                        evidence.getId(),
                        beforeStatus,
                        evidence.getVerificationStatus(),
                        evidence.getReviewedByAdminUserId(),
                        evidence.getReviewedAt()
                ),
                "PLACE",
                String.valueOf(placeId)
        );
    }
}
