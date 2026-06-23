package com.typenull.pingdom.shared.security.handler;

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

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter;

    public JwtAccessDeniedHandler(
            ObjectMapper objectMapper,
            CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter
    ) {
        this.objectMapper = objectMapper;
        this.corsErrorResponseHeaderWriter = corsErrorResponseHeaderWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        corsErrorResponseHeaderWriter.apply(request, response);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "message", "관리자 권한이 필요합니다.",
                "code", "ACCESS_DENIED"
        )));
    }
}
