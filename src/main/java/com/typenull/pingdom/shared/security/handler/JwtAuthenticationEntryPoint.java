package com.typenull.pingdom.shared.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
// JWT 인증 실패 시 401 JSON 응답 생성 클래스
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter;

    public JwtAuthenticationEntryPoint(
            ObjectMapper objectMapper,
            CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter
    ) {
        this.objectMapper = objectMapper;
        this.corsErrorResponseHeaderWriter = corsErrorResponseHeaderWriter;
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

        corsErrorResponseHeaderWriter.apply(request, response);
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "message", errorCode.getMessage(),
                "code", errorCode.name()
        )));
    }
}
