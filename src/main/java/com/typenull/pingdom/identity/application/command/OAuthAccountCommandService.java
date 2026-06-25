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

@Service
@RequiredArgsConstructor
public class OAuthAccountCommandService {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public User linkGoogleAccount(Long userId, String providerId, String email) {
        User user = findActiveUser(userId);
        validateGoogleAccountAttributes(providerId, email);

        if (!user.getEmail().equalsIgnoreCase(email.trim())) {
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
