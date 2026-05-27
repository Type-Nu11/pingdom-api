package com.typenull.pingdom.domain.auth.service.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
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

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "OAUTH2_LOGIN_FAILED";
        }

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", "OAUTH2_LOGIN_FAILED")
                .queryParam("message", message)
                .build()
                .toUriString();

        response.sendRedirect(targetUrl);
    }
}
