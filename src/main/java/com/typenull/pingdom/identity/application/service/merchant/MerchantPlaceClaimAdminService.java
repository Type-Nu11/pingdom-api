package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantPlaceClaimListItemResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantPlaceClaimPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.AdminMerchantPlaceClaimResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimReviewRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantPlaceClaimAdminService {

    private final MerchantPlaceClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminMerchantPlaceClaimPageResponse list(MerchantPlaceClaimStatus status, int page, int limit) {
        PageRequest pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<MerchantPlaceClaim> result = status == null
                ? claimRepository.findAll(pageable)
                : claimRepository.findAllByStatus(status, pageable);
        return new AdminMerchantPlaceClaimPageResponse(
                result.getContent().stream().map(AdminMerchantPlaceClaimListItemResponse::from).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public AdminMerchantPlaceClaimResponse get(Long claimId) {
        return AdminMerchantPlaceClaimResponse.from(requireClaim(claimId));
    }

    @Transactional
    public AdminMerchantPlaceClaimResponse review(
            Long adminUserId,
            Long claimId,
            MerchantPlaceClaimReviewRequest request
    ) {
        boolean approved = Boolean.TRUE.equals(request.approved());
        if (approved) {
            MerchantPlaceClaim claimSnapshot = requireClaim(claimId);
            requireEligibleMerchantOwnerForUpdate(
                    claimSnapshot.getMerchantOwnerUserId(),
                    LocalDateTime.now(clock)
            );
        }

        MerchantPlaceClaim claim = requireClaimForUpdate(claimId);
        MerchantPlaceClaimStatus beforeStatus = claim.getStatus();
        if (!claim.isPending()) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PLACE_CLAIM_STATE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            if (approved) {
                approveClaim(claim, adminUserId, request.reason().trim(), now);
            } else {
                claim.reject(adminUserId, request.reason().trim(), now);
            }
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PLACE_CLAIM_STATE);
        }
        recordAudit(adminUserId, claim, beforeStatus, request.reason().trim());
        return AdminMerchantPlaceClaimResponse.from(claim);
    }

    private void approveClaim(MerchantPlaceClaim claim, Long adminUserId, String reason, LocalDateTime now) {
        mapPlaceRepository.findByIdForUpdate(claim.getPlaceId())
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_NOT_FOUND));
        if (ownerPlaceRepository.existsById(claim.getPlaceId())) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_ALREADY_ASSIGNED);
        }
        claim.approve(adminUserId, reason, now);
        ownerPlaceRepository.save(MerchantOwnerPlace.builder()
                .placeId(claim.getPlaceId())
                .merchantOwnerUserId(claim.getMerchantOwnerUserId())
                .createdAt(now)
                .build());
    }

    private void requireEligibleMerchantOwnerForUpdate(Long userId, LocalDateTime now) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIMANT_NOT_ELIGIBLE));
        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIMANT_NOT_ELIGIBLE));
        if (!user.isMerchantOwner()
                || user.isWithdrawn()
                || user.isCurrentlyBanned(now)
                || profile.getStatus() != MerchantOwnerStatus.ACTIVE
                || !verification.isFullyApproved()
                || !verification.matchesBusinessName(profile.getBusinessName())) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIMANT_NOT_ELIGIBLE);
        }
    }

    private MerchantPlaceClaim requireClaim(Long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIM_NOT_FOUND));
    }

    private MerchantPlaceClaim requireClaimForUpdate(Long claimId) {
        return claimRepository.findByIdForUpdate(claimId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIM_NOT_FOUND));
    }

    private void recordAudit(
            Long adminUserId,
            MerchantPlaceClaim claim,
            MerchantPlaceClaimStatus beforeStatus,
            String reason
    ) {
        auditLogService.record(
                adminUserId,
                claim.getStatus() == MerchantPlaceClaimStatus.APPROVED
                        ? AdminAuditAction.MERCHANT_PLACE_CLAIM_APPROVED
                        : AdminAuditAction.MERCHANT_PLACE_CLAIM_REJECTED,
                AdminAuditTargetType.MERCHANT_PLACE_CLAIM,
                claim.getId(),
                reason,
                Map.of("status", beforeStatus),
                Map.of(
                        "status", claim.getStatus(),
                        "merchantOwnerUserId", claim.getMerchantOwnerUserId(),
                        "placeId", claim.getPlaceId()
                )
        );
    }
}
