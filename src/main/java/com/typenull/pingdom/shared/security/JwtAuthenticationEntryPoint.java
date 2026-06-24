package com.typenull.pingdom.shared.security;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.web.RequestIdFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
// JWT 인증 실패 시 401 JSON 응답 생성 클래스
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter;
    private final AuthMetrics authMetrics;

    public JwtAuthenticationEntryPoint(
            ObjectMapper objectMapper,
            CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter,
            AuthMetrics authMetrics
    ) {
        this.objectMapper = objectMapper;
        this.corsErrorResponseHeaderWriter = corsErrorResponseHeaderWriter;
        this.authMetrics = authMetrics;
    }

    @Override
    // 인증 실패 응답 작성 메서드
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        boolean expired = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRED_ATTRIBUTE));
        AuthErrorCode errorCode = expired ? AuthErrorCode.EXPIRED_TOKEN : AuthErrorCode.INVALID_TOKEN;
        authMetrics.recordAuthFailure(errorCode, "security_entry_point");
        logAuthFailureDiagnostic(request, errorCode);

        corsErrorResponseHeaderWriter.apply(request, response);
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "message", errorCode.getMessage(),
                "code", errorCode.name()
        )));
    }

    private void logAuthFailureDiagnostic(HttpServletRequest request, AuthErrorCode errorCode) {
        Object diagnosticAttribute = request.getAttribute(JwtAuthenticationFilter.AUTH_DIAGNOSTIC_ATTRIBUTE);
        if (diagnosticAttribute instanceof JwtAuthenticationFilter.AuthDiagnostic diagnostic) {
            log.warn(
                    "auth diagnostic failure: requestId={} method={} uri={} code={} authHeaderPresent={} bearerPrefixValid={} tokenStatus={} userId={} canAuthenticate={} authenticationSet={}",
                    diagnostic.requestId(),
                    diagnostic.method(),
                    diagnostic.uri(),
                    errorCode.name(),
                    diagnostic.authHeaderPresent(),
                    diagnostic.bearerPrefixValid(),
                    diagnostic.tokenStatus(),
                    diagnostic.userId(),
                    diagnostic.canAuthenticate(),
                    diagnostic.authenticationSet()
            );
            return;
        }

        if (!isFallbackDiagnosticPath(request.getRequestURI())) {
            return;
        }

        log.warn(
                "auth diagnostic failure: requestId={} method={} uri={} code={} authDiagnostic=missing",
                MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
                request.getMethod(),
                request.getRequestURI(),
                errorCode.name()
        );
    }

    private boolean isFallbackDiagnosticPath(String requestUri) {
        return "/place".equals(requestUri)
                || requestUri.startsWith("/place/")
                || "/users/me".equals(requestUri)
                || "/map/posts".equals(requestUri)
                || "/map/bookmarks".equals(requestUri)
                || "/firebase/fcm-token".equals(requestUri);
    }
}
