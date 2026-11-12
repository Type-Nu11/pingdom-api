package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.exception.UsersErrorCode;
import com.typenull.pingdom.identity.domain.exception.UsersException;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInvitationStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInvitationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 통합 신청 승인 트랜잭션 안에서 identity 소유 상태만 변경합니다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MerchantApplicationApprovalService {
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantPlaceInvitationRepository invitationRepository;

    public void prepare(MerchantData data, LocalDateTime now) {
        Long userId = data.userId();
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId).orElse(null);
        if (profile == null) {
            profileRepository.save(MerchantOwnerProfile.pending(userId, data.businessName(), data.displayName(),
                    data.description(), data.contactEmail(), data.contactPhone(), now));
        } else if (profile.getStatus() == MerchantOwnerStatus.PENDING) {
            profile.update(data.businessName(), data.displayName(), data.description(),
                    data.contactEmail(), data.contactPhone(), now);
        } else if (profile.getStatus() == MerchantOwnerStatus.REJECTED || profile.getStatus() == MerchantOwnerStatus.REVOKED) {
            profile.reapply(data.businessName(), data.displayName(), data.description(),
                    data.contactEmail(), data.contactPhone(), now);
        } else if (!profile.getBusinessName().equals(data.businessName())) {
            throw new IllegalStateException("활성 사업자의 상호 변경은 기존 사업자 정보 변경 절차를 사용해야 합니다.");
        } else {
            profile.update(data.businessName(), data.displayName(), data.description(),
                    data.contactEmail(), data.contactPhone(), now);
        }

        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId).orElse(null);
        if (verification == null) {
            verificationRepository.save(MerchantVerification.pending(userId, data.legalName(), data.businessName(),
                    data.encryptedBusinessRegistrationNumber(), now));
        } else if (verification.getIdentityStatus() == MerchantVerificationStatus.PENDING
                && verification.getBusinessStatus() == MerchantVerificationStatus.PENDING) {
            verification.update(data.legalName(), data.businessName(),
                    data.encryptedBusinessRegistrationNumber(), now);
        } else if (verification.getIdentityStatus() == MerchantVerificationStatus.REJECTED
                || verification.getBusinessStatus() == MerchantVerificationStatus.REJECTED) {
            verification.reapply(data.legalName(), data.businessName(),
                    data.encryptedBusinessRegistrationNumber(), now);
        } else {
            verification.resubmit(data.legalName(), data.businessName(),
                    data.encryptedBusinessRegistrationNumber(), now);
        }
    }

    public void activate(Long userId, Long adminUserId, String reason, LocalDateTime now) {
        MerchantOwnerProfile profile = profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.PROFILE_NOT_FOUND));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UsersException(UsersErrorCode.USER_NOT_FOUND));
        profile.approve(adminUserId, reason, now);
        user.activateMerchantOwnerRole();
    }

    public void approveVerification(Long userId, Long adminUserId, String reason, LocalDateTime now) {
        MerchantVerification verification = verificationRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.VERIFICATION_NOT_FOUND));
        verification.review(adminUserId, true, true, reason, now);
    }

    /** 장소와 소유권 잠금·변경 전 스냅샷 이후 호출되며 오퍼 종료/flush는 호출자가 제어합니다. */
    public Long transferPlaceOwnership(
            Long placeId, Long expectedOwnerUserId, Long newOwnerUserId, LocalDateTime now
    ) {
        MerchantOwnerPlace currentOwner = ownerPlaceRepository.findByPlaceIdForUpdate(placeId).orElse(null);
        Long ownerId = currentOwner == null ? null : currentOwner.getMerchantOwnerUserId();
        if (!Objects.equals(expectedOwnerUserId, ownerId)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.PLACE_OWNERSHIP_CHANGED);
        }
        if (currentOwner == null) {
            ownerPlaceRepository.save(MerchantOwnerPlace.builder().placeId(placeId)
                    .merchantOwnerUserId(newOwnerUserId).createdAt(now).build());
            ensureOwnerMember(placeId, newOwnerUserId, now);
            return null;
        }
        synchronizePlaceTeam(placeId, newOwnerUserId, now);
        currentOwner.transferOwnership(newOwnerUserId);
        return ownerId;
    }

    private void synchronizePlaceTeam(Long placeId, Long newOwnerUserId, LocalDateTime now) {
        for (MerchantPlaceMember member : memberRepository.findAllByPlaceId(placeId)) {
            if (!member.getUserId().equals(newOwnerUserId) && member.getStatus() == MerchantPlaceMemberStatus.ACTIVE) {
                member.revoke(now);
            }
        }
        invitationRepository.findAllByPlaceIdAndStatus(placeId, MerchantPlaceInvitationStatus.PENDING)
                .forEach(invitation -> invitation.revoke(now));
        ensureOwnerMember(placeId, newOwnerUserId, now);
    }

    private void ensureOwnerMember(Long placeId, Long userId, LocalDateTime now) {
        MerchantPlaceMember owner = memberRepository.findByPlaceIdAndUserIdForUpdate(placeId, userId)
                .orElseGet(() -> MerchantPlaceMember.owner(placeId, userId, now));
        if (owner.getStatus() == MerchantPlaceMemberStatus.REVOKED) {
            owner.restoreAsOwner(userId, now);
        } else if (owner.getRole() != MerchantPlaceMemberRole.OWNER) {
            owner.promoteToOwner(now);
        }
        memberRepository.save(owner);
    }

    public record MerchantData(
            Long userId, String legalName, String businessName, String encryptedBusinessRegistrationNumber,
            String displayName, String description, String contactEmail, String contactPhone
    ) {
    }
}
