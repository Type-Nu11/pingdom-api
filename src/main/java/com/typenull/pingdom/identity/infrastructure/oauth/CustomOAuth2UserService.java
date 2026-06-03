package com.typenull.pingdom.identity.infrastructure.oauth;

import com.typenull.pingdom.identity.application.service.OAuthUserService;
import com.typenull.pingdom.identity.domain.AuthProvider;
import com.typenull.pingdom.identity.domain.User;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthUserService oAuthUserService;

    @Override
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
        Object subAttribute = attributes.get("sub");
        String providerId = (subAttribute != null) ? String.valueOf(subAttribute) : null;
        String email = (String) attributes.get("email");

        if (providerId == null || providerId.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_sub"), "Google 사용자 식별자(sub)를 찾을 수 없습니다.");
        }
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_email"), "Google 사용자 이메일을 찾을 수 없습니다.");
        }

        User user = oAuthUserService.provisionGoogleUser(providerId, email);

        return new CustomOAuth2User(
                user.getId(),
                user.getUsername(),
                user.getRole(),
                AuthProvider.GOOGLE,
                providerId,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                attributes,
                "sub"
        );
    }
}
