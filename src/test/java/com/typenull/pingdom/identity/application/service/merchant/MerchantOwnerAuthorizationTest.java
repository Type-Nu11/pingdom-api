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

    /**
     * JWT에는 점주 권한이 남아 있어도 현재 사용자 역할이 일반 사용자이면 활성 점주 접근을 거부하는지 검증.
     */
    @Test
    void rejectsRevokedOwnerRole() {
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

    /**
     * 현재 점주 역할과 활성 프로필, 승인된 본인·사업자 인증이 모두 있으면 활성 점주 접근을 허용하는지 검증.
     */
    @Test
    void authorizesVerifiedActiveOwner() {
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

    /**
     * 활성 점주 프로필이 있으면 인증 완료 전에도 승인 점주 판정은 통과하되 활성 점주 판정은 실패하는지 검증.
     */
    @Test
    void allowsApprovedOwnerBeforeVerification() {
        Long userId = 1L;
        User owner = User.builder().id(userId).role(UserRole.MERCHANT_OWNER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));
        when(profileRepository.existsByUserIdAndStatus(userId, MerchantOwnerStatus.ACTIVE)).thenReturn(true);

        MerchantOwnerAuthorization authorization = new MerchantOwnerAuthorization(
                userRepository,
                profileRepository,
                verificationRepository
        );

        assertThat(authorization.isApproved(authentication(userId))).isTrue();
        assertThat(authorization.isActive(authentication(userId))).isFalse();
    }

    /**
     * 점주 역할과 활성 프로필만으로는 인증 승인이 필요한 활성 점주 접근을 허용하지 않는지 검증.
     */
    @Test
    void rejectsOwnerWithoutApprovedVerification() {
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

    /**
     * 현재 DB 상태와 오래된 토큰 권한을 분리해 확인할 수 있도록 점주 권한을 가진 인증 객체를 생성.
     */
    private UsernamePasswordAuthenticationToken authentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                new JwtAuthenticatedUser(userId, "merchant"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT_OWNER"))
        );
    }
}
