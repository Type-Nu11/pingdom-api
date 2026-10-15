package com.typenull.pingdom.place.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.place.api.dto.route.WalkingRouteResponse;
import com.typenull.pingdom.place.api.dto.route.WalkingRouteResponse.Step;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TmapPedestrianClient {
    private final TmapPedestrianTransport transport;
    private final Properties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public TmapPedestrianClient(TmapPedestrianTransport transport, Properties properties,
            ObjectMapper mapper, MeterRegistry metrics) {
        this.transport = transport;
        this.properties = properties;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public WalkingRouteResponse findRoute(RouteCoordinate origin, RouteCoordinate destination) {
        if (!properties.isConfigured()) {
            log.warn("Route provider failure provider=tmap routeType=pedestrian outcome=configuration enabled={} appKeyPresent={}",
                    properties.enabled(), properties.appKey() != null && !properties.appKey().isBlank());
            throw new MapException(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
        }
        long started = System.nanoTime();
        String outcome = "communication";
        int status = -1;
        try {
            var response = transport.post(origin, destination);
            status = response.status();
            if (status == 400) {
                outcome = "invalid_request";
                throw new MapException(MapErrorCode.INVALID_ROUTE_REQUEST);
            }
            if (status == 404) {
                outcome = "not_found";
                throw new MapException(MapErrorCode.ROUTE_NOT_FOUND);
            }
            if (status == 401 || status == 403) {
                outcome = "authentication";
                throw unavailable();
            }
            if (status == 429) {
                outcome = "quota";
                throw unavailable();
            }
            if (status >= 500) {
                outcome = "provider_error";
                throw unavailable();
            }
            if (status >= 400) {
                outcome = "provider_rejection";
                throw unavailable();
            }
            outcome = "invalid_response";
            WalkingRouteResponse result = mapResponse(mapper.readTree(response.body()));
            outcome = "success";
            return result;
        } catch (MapException exception) {
            if (exception.getErrorCode() == MapErrorCode.INVALID_ROUTE_REQUEST) outcome = "invalid_request";
            else if (exception.getErrorCode() == MapErrorCode.ROUTE_NOT_FOUND) outcome = "not_found";
            else if (exception.getErrorCode() == MapErrorCode.ROUTE_PROVIDER_TIMEOUT) outcome = "timeout";
            throw exception;
        } catch (SocketTimeoutException exception) {
            outcome = "timeout";
            throw new MapException(MapErrorCode.ROUTE_PROVIDER_TIMEOUT);
        } catch (IOException exception) {
            throw unavailable();
        } finally {
            if (!"success".equals(outcome)) {
                log.warn("Route provider failure provider=tmap routeType=pedestrian outcome={} httpStatus={}",
                        outcome, status);
            }
            metrics.timer("pingdom.route.provider.duration", "provider", "tmap", "outcome", outcome)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private WalkingRouteResponse mapResponse(JsonNode body) {
        JsonNode features = body.path("features");
        if (!features.isArray()) throw unavailable();
        if (features.isEmpty()) throw new MapException(MapErrorCode.ROUTE_NOT_FOUND);
        long totalDistance = -1;
        long totalTime = -1;
        List<RouteCoordinate> path = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode featureProperties = feature.path("properties");
            if (totalDistance < 0 && featureProperties.path("totalDistance").isNumber()) {
                totalDistance = nonNegative(featureProperties, "totalDistance");
            }
            if (totalTime < 0 && featureProperties.path("totalTime").isNumber()) {
                totalTime = nonNegative(featureProperties, "totalTime");
            }
            JsonNode geometry = feature.path("geometry");
            if (!"LineString".equals(geometry.path("type").asText())) continue;
            List<RouteCoordinate> segment = coordinates(geometry.path("coordinates"));
            appendWithoutDuplicate(path, segment);
            steps.add(new Step(textOrNull(featureProperties, "description"),
                    optionalNonNegative(featureProperties, "distance"),
                    optionalNonNegative(featureProperties, "time"), segment));
        }
        if (totalDistance < 0 || totalTime < 0 || path.size() < 2) throw unavailable();
        return new WalkingRouteResponse("walk", "tmap", totalDistance, totalTime,
                List.copyOf(path), List.copyOf(steps));
    }

    private List<RouteCoordinate> coordinates(JsonNode nodes) {
        if (!nodes.isArray()) throw unavailable();
        List<RouteCoordinate> result = new ArrayList<>();
        for (JsonNode pair : nodes) {
            if (!pair.isArray() || pair.size() < 2 || !pair.get(0).isNumber() || !pair.get(1).isNumber()) throw unavailable();
            RouteCoordinate coordinate = new RouteCoordinate(pair.get(1).doubleValue(), pair.get(0).doubleValue());
            if (!coordinate.isValid()) throw unavailable();
            result.add(coordinate);
        }
        if (result.size() < 2) throw unavailable();
        return List.copyOf(result);
    }

    private void appendWithoutDuplicate(List<RouteCoordinate> target, List<RouteCoordinate> segment) {
        int start = !target.isEmpty() && target.getLast().equals(segment.getFirst()) ? 1 : 0;
        target.addAll(segment.subList(start, segment.size()));
    }

    private long nonNegative(JsonNode node, String field) {
        long value = node.path(field).longValue();
        if (value < 0) throw unavailable();
        return value;
    }

    private long optionalNonNegative(JsonNode node, String field) {
        return node.path(field).isNumber() ? nonNegative(node, field) : 0;
    }

    private String textOrNull(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).textValue() : null;
    }

    private MapException unavailable() { return new MapException(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE); }

    @ConfigurationProperties(prefix = "place.routes.tmap-pedestrian")
    public record Properties(Boolean enabled, String appKey, String baseUrl,
            Duration connectTimeout, Duration readTimeout, Duration requestTimeout) {
        public Properties {
            enabled = Boolean.TRUE.equals(enabled);
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://apis.openapi.sk.com" : baseUrl;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
            if (connectTimeout.isNegative() || connectTimeout.isZero()
                    || readTimeout.isNegative() || readTimeout.isZero()
                    || requestTimeout.isNegative() || requestTimeout.isZero()) {
                throw new IllegalArgumentException("TMAP 보행자 timeout은 0보다 커야 합니다.");
            }
            URI uri = URI.create(baseUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("TMAP 보행자 base URL은 인증정보 없는 HTTPS 주소여야 합니다.");
            }
        }
        public boolean isConfigured() { return enabled && appKey != null && !appKey.isBlank(); }
        @Override public String toString() { return "TmapPedestrianProperties[enabled=" + enabled + ", appKey=REDACTED]"; }
    }
}
