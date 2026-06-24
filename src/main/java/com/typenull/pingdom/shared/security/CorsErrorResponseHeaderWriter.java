package com.typenull.pingdom.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@Component
public class CorsErrorResponseHeaderWriter {

    private final CorsConfigurationSource corsConfigurationSource;

    public CorsErrorResponseHeaderWriter(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

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
