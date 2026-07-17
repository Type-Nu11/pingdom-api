package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimPageResponse;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimRequest;
import com.typenull.pingdom.identity.api.dto.merchant.MerchantPlaceClaimResponse;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceClaimRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantPlaceClaimService {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantPlaceClaimRepository claimRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final Clock clock;

    @Transactional
    public MerchantPlaceClaimResponse create(Long userId, MerchantPlaceClaimRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        requireEligibleMerchantOwnerForUpdate(userId, now);
        mapPlaceRepository.findByIdForUpdate(request.placeId())
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_NOT_FOUND));
        if (claimRepository.existsByPlaceIdAndStatus(request.placeId(), MerchantPlaceClaimStatus.PENDING)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIM_ALREADY_PENDING);
        }
        Long previousOwnerUserId = ownerPlaceRepository.findById(request.placeId())
                .map(MerchantOwnerPlace::getMerchantOwnerUserId)
                .orElse(null);
        if (userId.equals(previousOwnerUserId)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_ALREADY_ASSIGNED_TO_REQUESTER);
        }
        MerchantPlaceClaim claim = claimRepository.save(MerchantPlaceClaim.pending(
                userId,
                request.placeId(),
                previousOwnerUserId,
                request.reason().trim(),
                now
        ));
        return MerchantPlaceClaimResponse.from(claim);
    }

    @Transactional(readOnly = true)
    public MerchantPlaceClaimPageResponse list(Long userId, int page, int limit) {
        PageRequest pageable = pageRequest(page, limit);
        Page<MerchantPlaceClaim> result = claimRepository.findAllByMerchantOwnerUserId(userId, pageable);
        return new MerchantPlaceClaimPageResponse(
                result.getContent().stream().map(MerchantPlaceClaimResponse::from).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public MerchantPlaceClaimResponse get(Long userId, Long claimId) {
        MerchantPlaceClaim claim = claimRepository.findByIdAndMerchantOwnerUserId(claimId, userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIM_NOT_FOUND));
        return MerchantPlaceClaimResponse.from(claim);
    }

    @Transactional
    public MerchantPlaceClaimResponse cancel(Long userId, Long claimId) {
        MerchantPlaceClaim claim = claimRepository.findByIdForUpdate(claimId)
                .filter(found -> found.getMerchantOwnerUserId().equals(userId))
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_CLAIM_NOT_FOUND));
        try {
            claim.cancel(LocalDateTime.now(clock));
        } catch (IllegalStateException exception) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.INVALID_PLACE_CLAIM_STATE);
        }
        return MerchantPlaceClaimResponse.from(claim);
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

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(
                Math.max(page - 1, 0),
                Math.min(Math.max(limit, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }
}
