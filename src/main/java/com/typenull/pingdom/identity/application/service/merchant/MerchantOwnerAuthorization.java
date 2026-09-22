package com.typenull.pingdom.identity.application.service.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 메서드 보안 표현식에서 사업자 승인 상태와 검증 완료 상태를 구분해 확인합니다.
 * isApproved는 회원 역할·탈퇴·프로필을, isActive는 여기에 본인·사업자 검증 승인까지 확인합니다.
 */
@Component("merchantOwnerAuthorization")
@RequiredArgsConstructor
public class MerchantOwnerAuthorization {

    private final UserRepository userRepository;
    private final MerchantOwnerProfileRepository profileRepository;
    private final MerchantVerificationRepository verificationRepository;

    public boolean isApproved(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser principal)) {
            return false;
        }
        return userRepository.findById(principal.userId())
                .filter(user -> user.isMerchantOwner() && !user.isWithdrawn())
                .isPresent()
                && profileRepository.existsByUserIdAndStatus(principal.userId(), MerchantOwnerStatus.ACTIVE);
    }

    /**
     * 메서드 보안에서 JWT 주체의 점주 역할·미탈퇴 상태·ACTIVE 프로필과 본인·사업자 검증 승인을 모두 확인합니다.
     * 주체가 없거나 조건을 충족하지 않으면 false를 반환하며, 현재 정지 여부는 이 판정에 포함하지 않습니다.
     */
    public boolean isActive(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof JwtAuthenticatedUser principal)) {
            return false;
        }
        return userRepository.findById(principal.userId())
                .filter(user -> user.isMerchantOwner() && !user.isWithdrawn())
                .isPresent()
                && profileRepository.existsByUserIdAndStatus(principal.userId(), MerchantOwnerStatus.ACTIVE)
                && verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(
                        principal.userId(),
                        MerchantVerificationStatus.APPROVED,
                        MerchantVerificationStatus.APPROVED
                );
    }
}
