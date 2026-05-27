package com.typenull.pingdom.domain.auth.service.oauth;

import com.typenull.pingdom.domain.auth.domain.AuthProvider;
import com.typenull.pingdom.domain.auth.domain.OAuthAccount;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.repository.OAuthAccountRepository;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        if (!"google".equalsIgnoreCase(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "지원하지 않는 OAuth Provider 입니다: " + registrationId
            );
        }

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId = String.valueOf(attributes.get("sub"));
        String email = (String) attributes.get("email");

        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_sub"), "Google 사용자 식별자(sub)를 찾을 수 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_email"), "Google 사용자 이메일을 찾을 수 없습니다.");
        }

        OAuthAccount account = oAuthAccountRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElse(null);

        User user;
        if (account != null) {
            user = account.getUser();
        } else {
            if (userRepository.existsByEmail(email)) {
                throw new OAuth2AuthenticationException(new OAuth2Error("email_conflict"), "이미 로컬 계정으로 가입된 이메일입니다.");
            }

            user = userRepository.save(User.builder()
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
        }

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "sub"
        );
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

