package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerErrorCode;
import com.typenull.pingdom.identity.domain.exception.MerchantOwnerException;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMember;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMemberStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceMemberRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MerchantPlaceCapabilityPolicy {

    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public void require(Long actorId, Long placeId, MerchantPlaceCapability capability) {
        User actor = userRepository.findById(actorId)
                .filter(user -> !user.isWithdrawn() && !user.isCurrentlyBanned(LocalDateTime.now(clock)))
                .orElseThrow(() -> denied());
        MerchantPlaceMember member = memberRepository.findByPlaceIdAndUserId(placeId, actorId).orElse(null);
        MerchantPlaceMemberRole role = member == null ? ownerRole(actor, placeId) : activeRole(member);
        if (role == null || !allows(role, capability)) {
            throw denied();
        }
    }

    private MerchantPlaceMemberRole activeRole(MerchantPlaceMember member) {
        return member.getStatus() == MerchantPlaceMemberStatus.ACTIVE ? member.getRole() : null;
    }

    private MerchantPlaceMemberRole ownerRole(User actor, Long placeId) {
        return ownerPlaceRepository.findById(placeId)
                .filter(place -> place.getMerchantOwnerUserId().equals(actor.getId()))
                .map(place -> MerchantPlaceMemberRole.OWNER)
                .orElse(null);
    }

    private boolean allows(MerchantPlaceMemberRole role, MerchantPlaceCapability capability) {
        if (role == MerchantPlaceMemberRole.OWNER) {
            return true;
        }
        if (role == MerchantPlaceMemberRole.MANAGER) {
            return capability != MerchantPlaceCapability.TEAM_ROLE_MANAGE
                    && capability != MerchantPlaceCapability.PAYMENT_VIEW
                    && capability != MerchantPlaceCapability.SETTLEMENT_VIEW;
        }
        return capability == MerchantPlaceCapability.PLACE_INFO_VIEW
                || capability == MerchantPlaceCapability.RESERVATION_VIEW
                || capability == MerchantPlaceCapability.RESERVATION_CONFIRM
                || capability == MerchantPlaceCapability.RESERVATION_CANCEL
                || capability == MerchantPlaceCapability.TEAM_VIEW;
    }

    private MerchantOwnerException denied() {
        return new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);
    }
}
