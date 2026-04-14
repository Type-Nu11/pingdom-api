package com.typenull.pingdom.domain.auth.security;

import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// JWT 인증 실패 시 401 JSON 응답 생성 클래스
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    // 인증 실패 응답 작성 메서드
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        response.setStatus(AuthErrorCode.INVALID_TOKEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(buildUnauthorizedResponse());
    }

    // 인증 실패 JSON 응답 본문 생성 메서드
    private String buildUnauthorizedResponse() {
        return "{\"message\":\"" + AuthErrorCode.INVALID_TOKEN.getMessage()
                + "\",\"code\":\"" + AuthErrorCode.INVALID_TOKEN.name() + "\"}";
    }
}
