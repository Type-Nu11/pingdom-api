package com.typenull.pingdom.identity.infrastructure.oauth;

import com.typenull.pingdom.identity.application.command.OAuthAccountCommandService;
import com.typenull.pingdom.identity.application.command.OAuthUserService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuth2UserResolver {

    private final OAuthUserService oAuthUserService;
    private final OAuthAccountCommandService oAuthAccountCommandService;
    private final OAuth2LinkCookieService oAuth2LinkCookieService;
    private final OAuth2LinkTokenService oAuth2LinkTokenService;

    public User resolveGoogleUser(String providerId, String email) {
        return oAuth2LinkCookieService.readToken()
                .map(token -> linkGoogleAccount(token, providerId, email))
                .orElseGet(() -> oAuthUserService.provisionGoogleUser(providerId, email));
    }

    private User linkGoogleAccount(String linkToken, String providerId, String email) {
        try {
            Long userId = oAuth2LinkTokenService.parseUserId(linkToken);
            return oAuthAccountCommandService.linkGoogleAccount(userId, providerId, email);
        } catch (AuthException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(exception.getErrorCode().name()),
                    exception.getMessage()
            );
        }
    }
}
