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

/**
 * 탈퇴·현재 정지 여부와 장소 팀 역할을 바탕으로 기능별 접근을 제한.
 * 팀원 행이 없는 경우에만 기존 소유 매핑을 OWNER로 인정하므로 비활성 팀원의 소유 매핑을 통한 우회는 불가.
 */
@Component
@RequiredArgsConstructor
public class MerchantPlaceCapabilityPolicy {

    private final MerchantPlaceMemberRepository memberRepository;
    private final MerchantOwnerPlaceRepository ownerPlaceRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 탈퇴·정지되지 않은 요청자의 활성 장소 팀 역할로 지정 기능 접근을 검사하고 미충족 시 팀 권한 오류 발생.
     * 팀원 행이 없을 때만 소유 매핑을 OWNER로 인정하며 비활성 팀원의 소유 매핑을 통한 우회는 불가.
     */
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
