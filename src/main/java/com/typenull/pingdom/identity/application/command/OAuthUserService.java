package com.typenull.pingdom.identity.application.command;

import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.OAuthAccount;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthUserService {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User provisionGoogleUser(String providerId, String email) {
        OAuthAccount account = oAuthAccountRepository.findWithUserByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElse(null);

        if (account != null) {
            return account.getUser();
        }

        if (userRepository.existsByEmail(email)) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_conflict"), "이미 로컬 계정으로 가입된 이메일입니다.");
        }

        User user = userRepository.save(User.builder()
                .username(generateUniqueUsername(email))
                .email(email)
                .emailVerified(true)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
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
