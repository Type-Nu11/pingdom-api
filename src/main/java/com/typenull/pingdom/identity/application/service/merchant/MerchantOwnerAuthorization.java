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
