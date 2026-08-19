package com.typenull.pingdom.shared.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.exception.ErrorCode;
import com.typenull.pingdom.shared.security.cors.CorsErrorResponseHeaderWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** 인증·인가 단계에서 발생한 보안 오류를 공통 JSON 응답으로 기록합니다. */
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final CorsErrorResponseHeaderWriter corsErrorResponseHeaderWriter;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        corsErrorResponseHeaderWriter.apply(request, response);
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.from(errorCode)));
    }
}
