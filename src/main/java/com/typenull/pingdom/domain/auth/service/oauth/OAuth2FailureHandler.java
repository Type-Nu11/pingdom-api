package com.typenull.pingdom.domain.auth.service.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

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

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "OAUTH2_LOGIN_FAILED");
        body.put("message", "구글 로그인에 실패했습니다. 서버 로그를 확인해주세요.");
        body.put("error", exception.getClass().getSimpleName());
        body.put("details", exception.getMessage());
        Throwable cause = exception.getCause();
        if (cause != null) {
            body.put("cause", cause.getClass().getSimpleName());
            body.put("causeDetails", cause.getMessage());
        }
        String error = request.getParameter("error");
        if (error != null && !error.isBlank()) {
            body.put("providerError", error);
        }
        String errorDescription = request.getParameter("error_description");
        if (errorDescription != null && !errorDescription.isBlank()) {
            body.put("providerErrorDescription", errorDescription);
        }
        body.put("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
