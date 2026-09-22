package com.typenull.pingdom.identity.infrastructure.oauth;

import com.typenull.pingdom.identity.application.command.OAuthAccountCommandService;
import com.typenull.pingdom.identity.application.command.OAuthUserService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

/**
 * 현재 요청의 연결 쿠키 존재 여부로 기존 회원 연결과 일반 Google 로그인·가입을 분기합니다.
 * 연결 실패를 OAuth 인증 예외로 변환하며 실패한 연결을 신규 가입으로 대체하지 않습니다.
 */
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
