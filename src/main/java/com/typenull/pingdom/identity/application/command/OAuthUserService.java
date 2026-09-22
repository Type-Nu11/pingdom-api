package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.OAuthAccount;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google 식별자로 기존 연결을 조회하거나 신규 회원과 OAuth 연결을 함께 저장.
 * 동일 이메일의 기존 회원은 자동 병합 대상에서 제외하며 신규 회원의 로컬 비밀번호 로그인을 비활성화.
 */
@Service
@RequiredArgsConstructor
public class OAuthUserService {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 기존 Google 연결의 회원을 반환하되 탈퇴 회원은 거절. 동일 이메일만 있는 회원은 자동 병합 대상에서 제외.
     * 새 계정이면 이메일 인증 완료·로컬 로그인 비활성 상태의 회원과 Google 연결을 같은 트랜잭션에 저장.
     */
    @Transactional
    public User provisionGoogleUser(String providerId, String email) {
        OAuthAccount account = oAuthAccountRepository.findWithUserByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElse(null);

        if (account != null) {
            User user = account.getUser();
            if (user.isWithdrawn()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(AuthErrorCode.USER_WITHDRAWN.name()),
                        AuthErrorCode.USER_WITHDRAWN.getMessage()
                );
            }
            return user;
        }

        if (userRepository.existsByEmail(email)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(AuthErrorCode.OAUTH_EMAIL_CONFLICT.name()),
                    AuthErrorCode.OAUTH_EMAIL_CONFLICT.getMessage()
            );
        }

        User user = userRepository.save(User.builder()
                .username(generateUniqueUsername(email))
                .email(email)
                .emailVerified(true)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .localPasswordEnabled(false)
                .birthYear(0)
                .language("und")
                .country("UNKNOWN")
                .build());

        oAuthAccountRepository.save(OAuthAccount.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId(providerId)
                .user(user)
                .build());

        return user;
    }

    private String generateUniqueUsername(String email) {
        String base = email;
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }

        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String candidate = base;
        int maxBaseLength = Math.max(1, 50 - 1 - suffix.length());
        if (candidate.length() > maxBaseLength) {
            candidate = candidate.substring(0, maxBaseLength);
        }
        return candidate + "_" + suffix;
    }
}
