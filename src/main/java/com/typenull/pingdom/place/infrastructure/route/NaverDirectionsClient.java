package com.typenull.pingdom.place.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.place.api.dto.route.RouteResponse;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 공급자 HTTP 상태·본문 코드를 분리하여 해석하며 원본 본문·좌표·키를 로그나 공개 오류에 넣지 않습니다. */
@Component
public class NaverDirectionsClient {
    private final NaverDirectionsTransport transport;
    private final Properties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public NaverDirectionsClient(NaverDirectionsTransport transport,
            Properties properties, ObjectMapper mapper, MeterRegistry metrics) {
        this.transport = transport;
        this.properties = properties;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public RouteResponse findRoute(RouteCoordinate origin, RouteCoordinate destination) {
        if (!properties.isConfigured()) {
            throw new MapException(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
        }
        long started = System.nanoTime();
        // 태그는 고정 분류만 사용하여 키·좌표 유출과 고유 시계열 증가를 방지합니다.
        String outcome = "unavailable";
        try {
            var response = transport.get(origin, destination);
            int status = response.status();
            if (status == 401 || status == 403) {
                outcome = "authentication";
                throw unavailable();
            }
            if (status == 429) {
                outcome = "quota";
                throw unavailable();
            }
            if (status == 504) {
                outcome = "timeout";
                throw new MapException(MapErrorCode.ROUTE_PROVIDER_TIMEOUT);
            }
            if (status != 200 && status != 400) {
                throw unavailable();
            }
            outcome = "invalid_response";
            JsonNode body = mapper.readTree(response.body());
            if (body == null || !body.path("code").isIntegralNumber() || !body.path("code").canConvertToInt()) {
                throw unavailable();
            }
            int code = body.path("code").intValue();
            if (code == 1 || code == 5) {
                outcome = "invalid_request";
                throw new MapException(MapErrorCode.INVALID_ROUTE_REQUEST);
            }
            if (code == 2 || code == 3 || code == 4) {
                outcome = "not_found";
                throw new MapException(MapErrorCode.ROUTE_NOT_FOUND);
            }
            if (code != 0 || status != 200) {
                throw unavailable();
            }
            RouteResponse result = mapRoute(body);
            outcome = "success";
            return result;
        } catch (IOException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    outcome = "timeout";
                    throw new MapException(MapErrorCode.ROUTE_PROVIDER_TIMEOUT);
                }
            }
            throw unavailable();
        } finally {
            metrics.timer("pingdom.route.provider.duration", "provider", "naver", "outcome", outcome)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private RouteResponse mapRoute(JsonNode body) {
        JsonNode routes = body.path("route").path("traoptimal");
        if (!routes.isArray() || routes.isEmpty()) {
            throw unavailable();
        }
        JsonNode route = routes.get(0);
        long distance = nonNegativeLong(route.path("summary").path("distance"));
        long milliseconds = nonNegativeLong(route.path("summary").path("duration"));
        JsonNode points = route.path("path");
        if (!points.isArray() || points.size() < 2) {
            throw unavailable();
        }
        List<RouteCoordinate> path = new ArrayList<>(points.size());
        for (JsonNode point : points) {
            if (!point.isArray() || point.size() != 2 || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                throw unavailable();
            }
            RouteCoordinate coordinate = new RouteCoordinate(point.get(1).doubleValue(), point.get(0).doubleValue());
            if (!coordinate.isValid()) {
                throw unavailable();
            }
            path.add(coordinate);
        }
        // 덧셈 오버플로 없이 밀리초의 나머지가 있는 경우에만 1초 올립니다.
        long seconds = milliseconds / 1000 + (milliseconds % 1000 == 0 ? 0 : 1);
        return new RouteResponse("car", "naver", distance, seconds, List.copyOf(path));
    }

    private long nonNegativeLong(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw unavailable();
        }
        return value.longValue();
    }

    private MapException unavailable() {
        return new MapException(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
    }

    @ConfigurationProperties(prefix = "place.routes.naver")
    public record Properties(Boolean enabled, String clientId, String clientSecret, String baseUrl,
                             Duration connectTimeout, Duration readTimeout, Duration requestTimeout) {
        public Properties {
            enabled = Boolean.TRUE.equals(enabled);
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://maps.apigw.ntruss.com" : baseUrl;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
            if (requestTimeout.toMillis() < 1 || requestTimeout.toMillis() > Integer.MAX_VALUE
                    || connectTimeout.toMillis() < 1 || readTimeout.toMillis() < 1
                    || connectTimeout.toMillis() > Integer.MAX_VALUE || readTimeout.toMillis() > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("경로 API timeout은 1ms 이상 int 범위여야 합니다.");
            }
            URI uri = URI.create(baseUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("경로 API base URL은 인증정보 없는 HTTPS 주소여야 합니다.");
            }
        }

        public boolean isConfigured() {
            return enabled && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "NaverDirectionsProperties[enabled=" + enabled + ", credentials=REDACTED]";
        }
    }
}
