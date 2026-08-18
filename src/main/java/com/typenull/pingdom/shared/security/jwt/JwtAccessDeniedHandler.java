package com.typenull.pingdom.shared.security.jwt;

import com.typenull.pingdom.shared.security.cors.CorsErrorResponseHeaderWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.exception.CommonErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        response.setStatus(CommonErrorCode.ACCESS_DENIED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.from(CommonErrorCode.ACCESS_DENIED)
        ));
    }
}
