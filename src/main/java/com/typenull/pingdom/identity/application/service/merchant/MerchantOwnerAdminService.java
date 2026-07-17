package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPlaceUpdateRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfilePageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerProfileResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerReviewRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantOwnerAdminService {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService auditLogService;
    private final UserAccessStatusService userAccessStatusService;
    private final TouristOfferRepository touristOfferRepository;
    private final Clock clock;

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

    @Transactional
    public MerchantOwnerProfileResponse approve(
            Long adminUserId,
            Long userId,
            MerchantOwnerReviewRequest request
    ) {
        User user = requireUserForUpdate(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.isWithdrawn() || user.isCurrentlyBanned(now)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.USER_ACCOUNT_NOT_ELIGIBLE);
        }
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        requireApprovedVerificationForUpdate(userId, profile.getBusinessName());
        MerchantOwnerStatus beforeStatus = profile.getStatus();

        try {
            profile.approve(adminUserId, now);
            user.activateMerchantOwnerRole();
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PROFILE_STATE);
        }
        replacePlaces(userId, request.normalizedPlaceIds(), now);
        userAccessStatusService.evict(userId);
        recordAudit(
                adminUserId,
                AdminAuditAction.MERCHANT_OWNER_APPROVED,
                userId,
                request.reason(),
                beforeStatus,
                profile.getStatus(),
                request.normalizedPlaceIds()
        );
        return response(profile);
    }

    @Transactional
    public MerchantOwnerProfileResponse reject(
            Long adminUserId,
            Long userId,
            MerchantOwnerReviewRequest request
    ) {
        User user = requireUserForUpdate(userId);
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantOwnerStatus beforeStatus = profile.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            profile.reject(adminUserId, now);
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
                request.reason(),
                beforeStatus,
                profile.getStatus(),
                Set.of()
        );
        return response(profile);
    }

    @Transactional
    public MerchantOwnerProfileResponse revoke(
            Long adminUserId,
            Long userId,
            MerchantOwnerReviewRequest request
    ) {
        User user = requireUserForUpdate(userId);
        MerchantOwnerProfile profile = requireProfileForUpdate(userId);
        MerchantOwnerStatus beforeStatus = profile.getStatus();
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            profile.revoke(adminUserId, now);
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

    private void requireApprovedVerificationForUpdate(Long userId, String businessName) {
        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_REQUIRED));
        if (!verification.isFullyApproved() || !verification.matchesBusinessName(businessName)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_REQUIRED);
        }
    }

    private User requireUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
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
}
