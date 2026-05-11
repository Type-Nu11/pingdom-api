package com.typenull.pingdom.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final CorsConfigurationSource corsConfigurationSource;

    public JwtAccessDeniedHandler(
            ObjectMapper objectMapper,
            CorsConfigurationSource corsConfigurationSource
    ) {
        this.objectMapper = objectMapper;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        applyCorsHeaders(request, response);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "message", "관리자 권한이 필요합니다.",
                "code", "ACCESS_DENIED"
        )));
    }

    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        CorsConfiguration corsConfiguration = corsConfigurationSource.getCorsConfiguration(request);
        if (corsConfiguration == null) {
            return;
        }

        String origin = request.getHeader("Origin");
        String allowedOrigin = corsConfiguration.checkOrigin(origin);
        if (allowedOrigin == null) {
            return;
        }

        response.setHeader("Access-Control-Allow-Origin", allowedOrigin);
        response.setHeader("Vary", "Origin");

        if (Boolean.TRUE.equals(corsConfiguration.getAllowCredentials())) {
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
    }
}
