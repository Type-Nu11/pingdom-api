package com.typenull.pingdom.shared.security.cors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/** 보안 체인에서 직접 작성하는 오류 응답에도 기존 CORS 설정의 허용 Origin·credential 헤더를 적용한다. */
@Component
public class CorsErrorResponseHeaderWriter {

    private final CorsConfigurationSource corsConfigurationSource;

    public CorsErrorResponseHeaderWriter(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    /** 설정이 없거나 Origin이 허용되지 않으면 헤더를 추가하지 않는다. 허용된 Origin만 응답에 반영한다. */
    public void apply(HttpServletRequest request, HttpServletResponse response) {
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
