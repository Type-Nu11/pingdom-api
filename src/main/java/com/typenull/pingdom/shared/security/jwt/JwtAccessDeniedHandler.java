package com.typenull.pingdom.shared.security.jwt;

import com.typenull.pingdom.shared.exception.CommonErrorCode;
import com.typenull.pingdom.shared.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 인증 후 권한이 부족한 요청을 공통 ACCESS_DENIED 응답으로 변환한다. */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter securityErrorResponseWriter;

    public JwtAccessDeniedHandler(SecurityErrorResponseWriter securityErrorResponseWriter) {
        this.securityErrorResponseWriter = securityErrorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        securityErrorResponseWriter.write(request, response, CommonErrorCode.ACCESS_DENIED);
    }
}
