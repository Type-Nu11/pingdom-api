package com.typenull.pingdom.place.api;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 기존 API의 오류 계약을 바꾸지 않고 경로 API의 파싱·호출 제한 오류만 정규화합니다. */
@Order(-1)
@RestControllerAdvice(assignableTypes = RouteController.class)
public class RouteExceptionHandler {
    private final AbuseRateLimitProperties properties;

    public RouteExceptionHandler(AbuseRateLimitProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidBody() {
        return ResponseEntity.badRequest().body(ErrorResponse.from(MapErrorCode.INVALID_ROUTE_REQUEST));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> rateLimited() {
        Duration userWindow = properties.routeQueryUser().window();
        Duration ipWindow = properties.routeQueryIp().window();
        Duration wait = userWindow.compareTo(ipWindow) >= 0 ? userWindow : ipWindow;
        long seconds = wait.getSeconds() + (wait.getNano() == 0 ? 0 : 1);
        return ResponseEntity.status(429).header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(ErrorResponse.from(MapErrorCode.ROUTE_RATE_LIMITED));
    }
}
