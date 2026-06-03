package com.typenull.pingdom.identity.infrastructure.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    @Value("${oauth2.redirect-uri:http://localhost:5173/oauth2/redirect}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        log.warn("OAuth2 login failed: uri={}, query={}, message={}",
                request.getRequestURI(),
                request.getQueryString(),
                exception.getMessage(),
                exception);

        String errorCode = "OAUTH2_LOGIN_FAILED";
        String message = "로그인에 실패했습니다. 다시 시도해주세요.";

        if (exception instanceof OAuth2AuthenticationException oauthException) {
            errorCode = oauthException.getError().getErrorCode();
            if (oauthException.getMessage() != null && !oauthException.getMessage().isBlank()) {
                message = oauthException.getMessage();
            }
        }

        String targetUrl = UriComponentsBuilder.fromUriString(normalizeRedirectUri(redirectUri))
                .queryParam("error", errorCode)
                .queryParam("message", message)
                .encode()
                .build()
                .toUriString();

        response.sendRedirect(targetUrl);
    }

    private String normalizeRedirectUri(String value) {
        if (value == null) {
            return "http://localhost:5173/oauth2/redirect";
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "http://localhost:5173/oauth2/redirect";
        }

        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }
}
