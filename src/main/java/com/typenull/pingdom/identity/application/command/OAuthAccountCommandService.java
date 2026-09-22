package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.OAuthAccount;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 활성 회원의 Google 계정 연결·해제를 처리.
 * 연결 시 이메일 일치를 확인하고 마지막 OAuth 연결을 해제할 때 로컬 비밀번호를 검증.
 */
@Service
@RequiredArgsConstructor
public class OAuthAccountCommandService {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 탈퇴·정지되지 않은 회원의 이메일과 Google 이메일이 일치할 때 공급자 식별자를 연결하고 회원을 반환.
     * 같은 회원에 이미 연결됐으면 그대로 반환하고, 다른 회원의 연결이나 누락된 Google 속성은 거절.
     */
    @Transactional
    public User linkGoogleAccount(Long userId, String providerId, String email) {
        User user = findActiveUser(userId);
        validateGoogleAccountAttributes(providerId, email);

        if (!email.trim().equalsIgnoreCase(user.getEmail())) {
            throw new AuthException(AuthErrorCode.OAUTH_EMAIL_MISMATCH);
        }

        OAuthAccount existingAccount = oAuthAccountRepository.findWithUserByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElse(null);
        if (existingAccount != null) {
            if (Objects.equals(existingAccount.getUser().getId(), user.getId())) {
                return user;
            }
            throw new AuthException(AuthErrorCode.OAUTH_ACCOUNT_ALREADY_LINKED);
        }

        oAuthAccountRepository.save(OAuthAccount.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId(providerId)
                .user(user)
                .build());
        return user;
    }

    /**
     * 탈퇴·정지되지 않은 회원의 Google 연결을 제거하며 미연결 상태는 거절.
     * 마지막 OAuth 연결이면 로컬 비밀번호 사용 가능 여부와 현재 비밀번호를 확인해 로그인 수단 상실을 차단.
     */
    @Transactional
    public void unlinkGoogleAccount(Long userId, String currentPassword) {
        User user = findActiveUser(userId);
        OAuthAccount account = oAuthAccountRepository.findByUser_IdAndProvider(user.getId(), AuthProvider.GOOGLE)
                .orElseThrow(() -> new AuthException(AuthErrorCode.OAUTH_ACCOUNT_NOT_LINKED));

        if (isLastOAuthAccount(user.getId())) {
            verifyCurrentPassword(user, currentPassword);
        }

        oAuthAccountRepository.delete(account);
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (user.isWithdrawn()) {
            throw new AuthException(AuthErrorCode.USER_WITHDRAWN);
        }
        if (user.isCurrentlyBanned(LocalDateTime.now(clock))) {
            throw new AuthException(AuthErrorCode.USER_BANNED);
        }
        return user;
    }

    private void validateGoogleAccountAttributes(String providerId, String email) {
        if (!StringUtils.hasText(providerId) || !StringUtils.hasText(email)) {
            throw new AuthException(AuthErrorCode.OAUTH_LINK_TOKEN_INVALID);
        }
    }

    private boolean isLastOAuthAccount(Long userId) {
        return oAuthAccountRepository.countByUser_Id(userId) <= 1;
    }

    private void verifyCurrentPassword(User user, String currentPassword) {
        if (!user.isLocalPasswordEnabled()) {
            throw new AuthException(AuthErrorCode.OAUTH_LOCAL_PASSWORD_REQUIRED);
        }
        if (!StringUtils.hasText(currentPassword)) {
            throw new AuthException(AuthErrorCode.OAUTH_PASSWORD_CONFIRMATION_REQUIRED);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }
}
