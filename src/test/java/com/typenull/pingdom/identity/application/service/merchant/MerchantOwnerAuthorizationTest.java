package com.typenull.pingdom.identity.application.service.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class MerchantOwnerAuthorizationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantOwnerProfileRepository profileRepository;

    @Mock
    private MerchantVerificationRepository verificationRepository;

    @Test
    void staleOwnerJwtIsRejectedAfterRoleRevocation() {
        Long userId = 1L;
        User revokedUser = User.builder().id(userId).role(UserRole.USER).build();
        UsernamePasswordAuthenticationToken authentication = authentication(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(revokedUser));

        MerchantOwnerAuthorization authorization = new MerchantOwnerAuthorization(
                userRepository,
                profileRepository,
                verificationRepository
        );

        assertThat(authorization.isActive(authentication)).isFalse();
    }

    @Test
    void currentActiveOwnerIsAuthorized() {
        Long userId = 1L;
        User owner = User.builder().id(userId).role(UserRole.MERCHANT_OWNER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(profileRepository.existsByUserIdAndStatus(userId, MerchantOwnerStatus.ACTIVE)).thenReturn(true);
        when(verificationRepository.existsByUserIdAndIdentityStatusAndBusinessStatus(
                userId,
                MerchantVerificationStatus.APPROVED,
                MerchantVerificationStatus.APPROVED
        )).thenReturn(true);

        MerchantOwnerAuthorization authorization = new MerchantOwnerAuthorization(
                userRepository,
                profileRepository,
                verificationRepository
        );

        assertThat(authorization.isActive(authentication(userId))).isTrue();
    }

    @Test
    void activeOwnerWithoutApprovedVerificationIsRejected() {
        Long userId = 1L;
        User owner = User.builder().id(userId).role(UserRole.MERCHANT_OWNER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(profileRepository.existsByUserIdAndStatus(userId, MerchantOwnerStatus.ACTIVE)).thenReturn(true);

        MerchantOwnerAuthorization authorization = new MerchantOwnerAuthorization(
                userRepository,
                profileRepository,
                verificationRepository
        );

        assertThat(authorization.isActive(authentication(userId))).isFalse();
    }

    private UsernamePasswordAuthenticationToken authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                new JwtAuthenticatedUser(userId, "merchant"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_OWNER"))
        );
    }
}
