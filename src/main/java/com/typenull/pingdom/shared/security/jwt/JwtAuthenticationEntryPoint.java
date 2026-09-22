package com.typenull.pingdom.shared.security.jwt;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** JWT 필터가 남긴 만료 표시로 오류 코드를 구분하고 인증 실패 메트릭과 JSON 응답을 작성. */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter securityErrorResponseWriter;
    private final AuthMetrics authMetrics;

    public JwtAuthenticationEntryPoint(
            SecurityErrorResponseWriter securityErrorResponseWriter,
            AuthMetrics authMetrics
    ) {
        this.securityErrorResponseWriter = securityErrorResponseWriter;
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

        securityErrorResponseWriter.write(request, response, errorCode);
    }
}
