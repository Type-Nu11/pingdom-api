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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MerchantPlaceInformationAccessPolicy {

    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public void requireManager(Long actorId, Long placeId) {
        User actor = userRepository.findById(actorId)
                .filter(user -> !user.isWithdrawn() && !user.isCurrentlyBanned(LocalDateTime.now(clock)))
                .orElseThrow(() -> new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED));

        if (!ownerPlaceRepository.existsById(placeId)) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.OWNER_PLACE_NOT_FOUND);
        }

        MerchantPlaceMember member = memberRepository.findByPlaceIdAndUserId(placeId, actorId).orElse(null);
        if (member != null) {
            if (member.getStatus() == MerchantPlaceMemberStatus.ACTIVE
                    && (member.getRole() == MerchantPlaceMemberRole.OWNER
                    || member.getRole() == MerchantPlaceMemberRole.MANAGER)) {
                return;
            }
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);
        }

        boolean owner = ownerPlaceRepository.findById(placeId)
                .map(place -> Objects.equals(place.getMerchantOwnerUserId(), actor.getId()))
                .orElse(false);
        if (!owner) {
            throw new MerchantOwnerException(MerchantOwnerErrorCode.MERCHANT_TEAM_PERMISSION_REQUIRED);
        }
    }
}
