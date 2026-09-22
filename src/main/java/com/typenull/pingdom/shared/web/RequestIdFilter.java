package com.typenull.pingdom.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 허용된 형태의 요청 ID 또는 새 UUID를 응답과 MDC에 연결해 요청 처리 로그를 추적한다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,100}$");

    /** 요청 ID를 체인 실행 전에 설정하고 예외가 발생해도 MDC에서 제거해 스레드 재사용 시 누출을 막는다. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /** 1~100자의 영숫자와 지정 구분자로만 구성된 헤더를 재사용하고 나머지는 UUID로 대체한다. */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(requestId) && SAFE_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
