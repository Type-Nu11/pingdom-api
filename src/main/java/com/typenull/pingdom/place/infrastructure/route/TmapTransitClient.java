package com.typenull.pingdom.place.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.place.api.dto.route.TransitRouteResponse;
import com.typenull.pingdom.place.api.dto.route.TransitRouteResponse.Itinerary;
import com.typenull.pingdom.place.api.dto.route.TransitRouteResponse.Leg;
import com.typenull.pingdom.place.api.dto.route.TransitRouteResponse.Stop;
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
public class TmapTransitClient {
    private final TmapTransitTransport transport;
    private final Properties properties;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public TmapTransitClient(TmapTransitTransport transport, Properties properties,
            ObjectMapper mapper, MeterRegistry metrics) {
        this.transport = transport;
        this.properties = properties;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public TransitRouteResponse findRoute(RouteCoordinate origin, RouteCoordinate destination) {
        if (!properties.isConfigured()) throw new MapException(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
        long started = System.nanoTime();
        String outcome = "unavailable";
        try {
            var response = transport.post(origin, destination);
            if (response.status() == 401 || response.status() == 403 || response.status() == 429) throw unavailable();
            if (response.status() >= 500) throw unavailable();
            JsonNode body = mapper.readTree(response.body());
            if (response.status() == 400) throw new MapException(MapErrorCode.INVALID_ROUTE_REQUEST);
            JsonNode itineraries = body.path("metaData").path("plan").path("itineraries");
            if (!itineraries.isArray() || itineraries.isEmpty()) {
                outcome = "not_found";
                throw new MapException(MapErrorCode.ROUTE_NOT_FOUND);
            }
            var result = new ArrayList<Itinerary>();
            for (JsonNode itinerary : itineraries) result.add(mapItinerary(itinerary));
            outcome = "success";
            return new TransitRouteResponse("transit", "tmap", List.copyOf(result));
        } catch (SocketTimeoutException exception) {
            outcome = "timeout";
            throw new MapException(MapErrorCode.ROUTE_PROVIDER_TIMEOUT);
        } catch (IOException exception) {
            throw unavailable();
        } finally {
            metrics.timer("pingdom.route.provider.duration", "provider", "tmap", "outcome", outcome)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private Itinerary mapItinerary(JsonNode node) {
        List<Leg> legs = new ArrayList<>();
        if (!node.path("legs").isArray()) throw unavailable();
        for (JsonNode leg : node.path("legs")) legs.add(mapLeg(leg));
        return new Itinerary(nonNegative(node, "totalTime"), nonNegative(node, "totalDistance"),
                node.path("transferCount").asInt(0), nonNegative(node, "totalWalkDistance", "walkDistance"),
                nonNegative(node, "totalWalkTime"), nonNegative(node.path("fare").path("regular"), "totalFare"),
                List.copyOf(legs));
    }

    private Leg mapLeg(JsonNode node) {
        List<RouteCoordinate> path = new ArrayList<>();
        collectLineString(node.path("passShape").path("linestring").asText(""), path);
        if (node.path("steps").isArray()) for (JsonNode step : node.path("steps"))
            collectLineString(step.path("linestring").asText(""), path);
        List<Stop> stations = new ArrayList<>();
        JsonNode stationNodes = node.path("passStopList").path("stations");
        if (stationNodes.isArray()) for (JsonNode station : stationNodes) stations.add(stop(station, "stationName", "stationID"));
        return new Leg(node.path("mode").asText(), nonNegative(node, "distance"), nonNegative(node, "sectionTime"),
                textOrNull(node, "route"), textOrNull(node, "routeId"), textOrNull(node, "routeColor"),
                node.path("type").isInt() ? node.path("type").intValue() : null,
                stop(node.path("start"), "name", null), stop(node.path("end"), "name", null),
                List.copyOf(path), List.copyOf(stations));
    }

    private void collectLineString(String value, List<RouteCoordinate> target) {
        if (value.isBlank()) return;
        for (String pair : value.trim().split("\\s+")) {
            String[] values = pair.split(",");
            if (values.length != 2) throw unavailable();
            try {
                RouteCoordinate coordinate = new RouteCoordinate(Double.valueOf(values[1]), Double.valueOf(values[0]));
                if (!coordinate.isValid()) throw unavailable();
                target.add(coordinate);
            } catch (NumberFormatException exception) { throw unavailable(); }
        }
    }

    private Stop stop(JsonNode node, String nameField, String idField) {
        if (!node.isObject()) return null;
        return new Stop(textOrNull(node, nameField), numberOrNull(node, "lat"), numberOrNull(node, "lon"),
                idField == null ? null : textOrNull(node, idField));
    }

    private long nonNegative(JsonNode node, String... fields) {
        for (String field : fields) if (node.path(field).isNumber()) {
            long value = node.path(field).longValue();
            if (value < 0) throw unavailable();
            return value;
        }
        return 0;
    }

    private Double numberOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNumber()) return value.doubleValue();
        if (value.isTextual()) try { return Double.valueOf(value.textValue()); } catch (NumberFormatException ignored) { return null; }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).textValue() : null;
    }

    private MapException unavailable() { return new MapException(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE); }

    @ConfigurationProperties(prefix = "place.routes.tmap-transit")
    public record Properties(Boolean enabled, String appKey, String baseUrl,
            Duration connectTimeout, Duration readTimeout, Duration requestTimeout) {
        public Properties {
            enabled = Boolean.TRUE.equals(enabled);
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://apis.openapi.sk.com" : baseUrl;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
            URI uri = URI.create(baseUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("TMAP 대중교통 base URL은 인증정보 없는 HTTPS 주소여야 합니다.");
            }
        }
        public boolean isConfigured() { return enabled && appKey != null && !appKey.isBlank(); }
        @Override public String toString() { return "TmapTransitProperties[enabled=" + enabled + ", appKey=REDACTED]"; }
    }
}
