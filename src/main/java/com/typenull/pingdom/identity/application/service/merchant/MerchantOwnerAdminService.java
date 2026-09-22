package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOnboardingUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceQualityUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfilePageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOnboardingStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.event.MerchantOnboardingUpdatedEvent;
import com.typenull.pingdom.identity.event.MerchantOperationalQualityUpdatedEvent;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.registration.PlaceRegistrationApplicationRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 프로필 심사와 장소 배정, 온보딩·운영 품질의 관리 작업을 조정합니다.
 * 거절·권한 회수 시 혜택을 종료하고 장소 연결을 제거하며, 변경 전후 값은 감사 로그에 남깁니다.
 */
@Service
@RequiredArgsConstructor
public class MerchantOwnerAdminService {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService auditLogService;
    private final UserAccessStatusService userAccessStatusService;
    private final TouristOfferRepository touristOfferRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AdminRoleAuthorizationService authorizationService;
    private final PlaceRegistrationApplicationRepository applicationRepository;

    /**
     * 점주 프로필을 선택한 상태로 걸러 수정 시각·사용자 ID 내림차순의 페이지로 반환합니다.
     * 페이지는 최소 1, 크기는 1~100으로 보정하고 각 프로필에 연결 장소 ID를 포함합니다.
     */
    @Transactional(readOnly = true)
    public MerchantOwnerProfilePageResponse list(MerchantOwnerStatus status, int page, int limit) {
        PageRequest pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("userId"))
        );
        Page<MerchantOwnerProfile> result = status == null
                ? profileRepository.findAll(pageable)
                : profileRepository.findAllByStatus(status, pageable);
        List<MerchantOwnerProfileResponse> profiles = result.getContent().stream()
                .map(this::response)
                .toList();
        return new MerchantOwnerProfilePageResponse(
                profiles,
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public MerchantOwnerProfileResponse get(Long userId) {
        return response(requireProfile(userId));
    }

    @Transactional(readOnly = true)
    public List<MerchantOwnerPlaceResponse> listPlaces(Long userId) {
        requireProfile(userId);
        return ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId).stream()
                .map(MerchantOwnerPlaceResponse::from)
                .toList();
    }

    /**
     * 심사 권한을 확인하고 사용자·프로필을 쓰기 잠금으로 읽어 Merchant 프로필과 사용자 역할을 활성화합니다.
     * 통합 신청 심사 대기, 탈퇴·현재 정지 계정, 승인 불가능한 프로필 상태는 거절합니다.
     * 접근 상태 로컬 캐시를 즉시 비우고 상태 변경 감사 로그를 기록한 프로필 응답을 반환합니다.
     */
    @Transactional
    public MerchantOwnerProfileResponse approve(
            Long adminUserId,
            Long userId,
        MerchantOwnerReviewRequest request
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        User user = requireUserForUpdate(userId);
        requireNoPendingUnifiedApplication(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.USER_ACCOUNT_NOT_ELIGIBLE);
        }
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantOwnerStatus beforeStatus = profile.getStatus();
        try {
            profile.approve(adminUserId, request.reason(), now);
            user.activateMerchantOwnerRole();
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        userAccessStatusService.evict(userId);
        recordAudit(
                adminUserId,
                AdminAuditAction.MERCHANT_OWNER_APPROVED,
                userId,
                request.reason(),
                beforeStatus,
                profile.getStatus(),
                Set.of()
        );
        return response(profile);
    }

    /**
     * 심사 권한과 필수 반려 사유를 검증하고 사용자·프로필을 잠가 통합 신청 대기가 없는 프로필을 반려합니다.
     * Merchant 역할을 회수하고 해당 사용자의 혜택을 종료하며 모든 장소 소유 연결을 삭제합니다.
     * 같은 DB 트랜잭션에 감사 기록을 남기고 접근 상태 로컬 캐시는 즉시 제거합니다.
     */
    @Transactional
    public MerchantOwnerProfileResponse reject(
            Long adminUserId,
            Long userId,
            MerchantOwnerReviewRequest request
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        String reviewReason = requireReviewReason(request.reason());
        User user = requireUserForUpdate(userId);
        requireNoPendingUnifiedApplication(userId);
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantOwnerStatus beforeStatus = profile.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            profile.reject(adminUserId, reviewReason, now);
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        user.revokeMerchantOwnerRole();
        touristOfferRepository.closeAllByMerchantOwnerUserId(userId, now);
        ownerPlaceRepository.deleteAllByMerchantOwnerUserId(userId);
        userAccessStatusService.evict(userId);
        recordAudit(
                adminUserId,
                AdminAuditAction.MERCHANT_OWNER_REJECTED,
                userId,
                reviewReason,
                beforeStatus,
                profile.getStatus(),
                Set.of()
        );
        return response(profile);
    }

    /**
     * 심사 권한을 확인하고 사용자·프로필을 잠가 회수 가능한 프로필을 REVOKED로 전이합니다.
     * Merchant 역할·장소 소유 연결을 회수하고 모든 혜택을 종료하며 감사 기록을 같은 DB 트랜잭션에 남깁니다.
     * 접근 상태 캐시는 현재 인스턴스에서 즉시 제거되며 다른 인스턴스 캐시까지 무효화하지는 않습니다.
     */
    @Transactional
    public MerchantOwnerProfileResponse revoke(
            Long adminUserId,
            Long userId,
            MerchantOwnerReviewRequest request
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        User user = requireUserForUpdate(userId);
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantOwnerStatus beforeStatus = profile.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            profile.revoke(adminUserId, request.reason(), now);
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        user.revokeMerchantOwnerRole();
        touristOfferRepository.closeAllByMerchantOwnerUserId(userId, now);
        ownerPlaceRepository.deleteAllByMerchantOwnerUserId(userId);
        userAccessStatusService.evict(userId);
        recordAudit(
                adminUserId,
                AdminAuditAction.MERCHANT_OWNER_REVOKED,
                userId,
                request.reason(),
                beforeStatus,
                profile.getStatus(),
                Set.of()
        );
        return response(profile);
    }

    @Transactional
    public MerchantOwnerProfileResponse replacePlaces(
            Long adminUserId,
            Long userId,
            MerchantOwnerPlaceUpdateRequest request
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.MERCHANT_REVIEW);
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        if (profile.getStatus() != MerchantOwnerStatus.ACTIVE) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        Set<Long> beforePlaceIds = new LinkedHashSet<>(placeIds(userId));
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> removedPlaceIds = new LinkedHashSet<>(beforePlaceIds);
        removedPlaceIds.removeAll(request.normalizedPlaceIds());
        if (!removedPlaceIds.isEmpty()) {
            touristOfferRepository.closeAllByMerchantOwnerUserIdAndPlaceIdIn(userId, removedPlaceIds, now);
        }
        replacePlaces(userId, request.normalizedPlaceIds(), now);
        recordAudit(
                adminUserId,
                AdminAuditAction.MERCHANT_OWNER_PLACES_UPDATED,
                userId,
                request.reason(),
                profile.getStatus(),
                profile.getStatus(),
                Map.of("before", beforePlaceIds, "after", request.normalizedPlaceIds())
        );
        return response(profile);
    }

    @Transactional
    public MerchantOwnerProfileResponse updateOnboarding(
            Long adminUserId,
            Long userId,
            MerchantOnboardingUpdateRequest request
    ) {
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantOnboardingStatus beforeStatus = profile.getOnboardingStatus();
        Integer beforeCompletionRate = profile.getOnboardingCompletionRate();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime completedAt = request.status() == MerchantOnboardingStatus.COMPLETED
                ? request.completedAt() == null ? now : request.completedAt()
                : request.completedAt();

        try {
            profile.updateOnboarding(request.status(), request.completionRate(), completedAt, now);
        } catch (IllegalArgumentException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_ONBOARDING_METRIC);
        }
        auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_ONBOARDING_UPDATED,
                AdminAuditTargetType.MERCHANT_OWNER,
                userId,
                request.reason(),
                Map.of("status", beforeStatus, "completionRate", beforeCompletionRate),
                nullableMap(
                        "status", profile.getOnboardingStatus(),
                        "completionRate", profile.getOnboardingCompletionRate(),
                        "completedAt", profile.getOnboardingCompletedAt()
                )
        );
        eventPublisher.publishEvent(new MerchantOnboardingUpdatedEvent(
                userId,
                beforeStatus,
                profile.getOnboardingStatus(),
                beforeCompletionRate,
                profile.getOnboardingCompletionRate(),
                now
        ));
        return response(profile);
    }

    @Transactional
    public MerchantOwnerPlaceResponse updateOperationalQuality(
            Long adminUserId,
            Long userId,
            Long placeId,
            MerchantOwnerPlaceQualityUpdateRequest request
    ) {
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        if (profile.getStatus() != MerchantOwnerStatus.ACTIVE) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        MerchantOwnerPlace ownerPlace = ownerPlaceRepository.findByPlaceIdForUpdate(placeId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.OWNER_PLACE_NOT_FOUND));
        if (!ownerPlace.getMerchantOwnerUserId().equals(userId)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.OWNER_PLACE_NOT_FOUND);
        }

        MerchantOperationalQualityStatus beforeStatus = ownerPlace.getOperationalQualityStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime evaluatedAt = request.evaluatedAt() == null ? now : request.evaluatedAt();
        try {
            ownerPlace.updateOperationalQuality(
                    request.status(),
                    request.reservationResponseRate(),
                    request.reservationCancellationRate(),
                    request.noShowRate(),
                    evaluatedAt
            );
        } catch (IllegalArgumentException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_OPERATIONAL_QUALITY_METRIC);
        }
        auditLogService.record(
                adminUserId,
                AdminAuditAction.MERCHANT_OPERATIONAL_QUALITY_UPDATED,
                AdminAuditTargetType.MERCHANT_OWNER,
                userId,
                request.reason(),
                Map.of("placeId", placeId, "status", beforeStatus),
                Map.of(
                        "placeId", placeId,
                        "status", ownerPlace.getOperationalQualityStatus(),
                        "reservationResponseRate", ownerPlace.getReservationResponseRate(),
                        "reservationCancellationRate", ownerPlace.getReservationCancellationRate(),
                        "noShowRate", ownerPlace.getNoShowRate(),
                        "qualityEvaluatedAt", ownerPlace.getQualityEvaluatedAt()
                )
        );
        eventPublisher.publishEvent(new MerchantOperationalQualityUpdatedEvent(
                userId,
                placeId,
                beforeStatus,
                ownerPlace.getOperationalQualityStatus(),
                now
        ));
        return MerchantOwnerPlaceResponse.from(ownerPlace);
    }

    private void replacePlaces(Long userId, Set<Long> requestedPlaceIds, LocalDateTime now) {
        List<Long> placeIds = requestedPlaceIds.stream().sorted().toList();
        if (!placeIds.isEmpty()) {
            List<MapPlace> places = mapPlaceRepository.findAllByIdInForUpdate(placeIds);
            if (places.size() != placeIds.size()) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_NOT_FOUND);
            }
            boolean assignedToAnotherOwner = ownerPlaceRepository.findAllByPlaceIdIn(placeIds).stream()
                    .anyMatch(place -> !place.getMerchantOwnerUserId().equals(userId));
            if (assignedToAnotherOwner) {
                throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_ALREADY_ASSIGNED);
            }
        }

        ownerPlaceRepository.deleteAllByMerchantOwnerUserId(userId);
        List<MerchantOwnerPlace> mappings = new ArrayList<>();
        for (Long placeId : placeIds) {
            mappings.add(MerchantOwnerPlace.builder()
                    .placeId(placeId)
                    .merchantOwnerUserId(userId)
                    .createdAt(now)
                    .build());
        }
        ownerPlaceRepository.saveAll(mappings);
    }

    private MerchantOwnerProfile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND));
    }

    private MerchantOwnerProfile requireProfileForUpdate(Long userId) {
        return profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND));
    }

    private User requireUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
    }

    private void requireNoPendingUnifiedApplication(Long userId) {
        if (applicationRepository.existsByApplicantUserIdAndStatus(
                userId,
                PlaceRegistrationStatus.PENDING
        )) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.UNIFIED_APPLICATION_REVIEW_REQUIRED);
        }
    }

    private String requireReviewReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_REVIEW_REASON);
        }
        return reason.trim();
    }

    private MerchantOwnerProfileResponse response(MerchantOwnerProfile profile) {
        return MerchantOwnerProfileResponse.from(profile, placeIds(profile.getUserId()));
    }

    private List<Long> placeIds(Long userId) {
        return ownerPlaceRepository.findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(userId).stream()
                .map(MerchantOwnerPlace::getPlaceId)
                .toList();
    }

    private void recordAudit(
            Long adminUserId,
            AdminAuditAction action,
            Long userId,
            String reason,
            MerchantOwnerStatus beforeStatus,
            MerchantOwnerStatus afterStatus,
            Object places
    ) {
        auditLogService.record(
                adminUserId,
                action,
                AdminAuditTargetType.MERCHANT_OWNER,
                userId,
                reason,
                Map.of("status", beforeStatus),
                Map.of("status", afterStatus, "places", places)
        );
    }

    private Map<String, Object> nullableMap(Object... keyValues) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }
}
