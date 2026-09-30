package com.typenull.pingdom.place.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NaverDirectionsClientTest {
    private static final RouteCoordinate ORIGIN = new RouteCoordinate(37.5665, 126.9780);
    private static final RouteCoordinate DESTINATION = new RouteCoordinate(37.4979, 127.0276);
    private NaverDirectionsTransport transport;
    private SimpleMeterRegistry metrics;

    @BeforeEach
    void setUp() {
        transport = mock(NaverDirectionsTransport.class);
        metrics = new SimpleMeterRegistry();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,1", "1000,1", "1001,2", "1800000,1800", "9223372036854775807,9223372036854776"})
    void preservesRoadGeometryAndConvertsMilliseconds(long milliseconds, long seconds) throws Exception {
        respond(200, validBody(milliseconds));
        var result = api(true, "test-secret").findRoute(ORIGIN, DESTINATION);
        assertThat(result.mode()).isEqualTo("car");
        assertThat(result.provider()).isEqualTo("naver");
        assertThat(result.distanceMeters()).isEqualTo(12500);
        assertThat(result.durationSeconds()).isEqualTo(seconds);
        assertThat(result.path()).containsExactly(new RouteCoordinate(37.56, 126.97),
                new RouteCoordinate(37.52, 127.01), new RouteCoordinate(37.49, 127.02));
        assertThat(metrics.get("pingdom.route.provider.duration").tag("outcome", "success").timer().count()).isEqualTo(1);
        verify(transport).get(ORIGIN, DESTINATION);
    }

    @ParameterizedTest
    @CsvSource({"400,1,INVALID_ROUTE_REQUEST", "400,2,ROUTE_NOT_FOUND", "400,3,ROUTE_NOT_FOUND",
            "400,4,ROUTE_NOT_FOUND", "400,5,INVALID_ROUTE_REQUEST", "400,100,ROUTE_PROVIDER_UNAVAILABLE",
            "200,3,ROUTE_NOT_FOUND", "200,99,ROUTE_PROVIDER_UNAVAILABLE"})
    void mapsProviderBusinessErrors(int status, int code, MapErrorCode expected) throws Exception {
        respond(status, "{\"code\":" + code + ",\"message\":\"private provider details\"}");
        assertFailure(expected);
        verify(transport).get(ORIGIN, DESTINATION);
    }

    @ParameterizedTest
    @CsvSource({"401,authentication,ROUTE_PROVIDER_UNAVAILABLE", "403,authentication,ROUTE_PROVIDER_UNAVAILABLE",
            "429,quota,ROUTE_PROVIDER_UNAVAILABLE", "500,unavailable,ROUTE_PROVIDER_UNAVAILABLE",
            "503,unavailable,ROUTE_PROVIDER_UNAVAILABLE", "504,timeout,ROUTE_PROVIDER_TIMEOUT",
            "302,unavailable,ROUTE_PROVIDER_UNAVAILABLE"})
    void separatesProviderFailureFromUserAuthentication(int status, String outcome, MapErrorCode expected) throws Exception {
        respond(status, "");
        assertFailure(expected);
        assertThat(metrics.get("pingdom.route.provider.duration").tag("outcome", outcome).timer().count()).isEqualTo(1);
        verify(transport).get(ORIGIN, DESTINATION);
    }

    @Test
    void mapsNetworkTimeoutWithoutRetry() throws Exception {
        when(transport.get(ORIGIN, DESTINATION)).thenThrow(new SocketTimeoutException("private URL"));
        assertFailure(MapErrorCode.ROUTE_PROVIDER_TIMEOUT);
        verify(transport).get(ORIGIN, DESTINATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "null", "{}", "{\"code\":\"0\"}",
            "{\"code\":0,\"route\":{\"traoptimal\":[]}}",
            "{\"code\":0,\"route\":{\"traoptimal\":[{\"summary\":{\"distance\":1,\"duration\":1},\"path\":[]}]}}"})
    void neverReturnsSuccessForMissingOrInvalidRoute(String body) throws Exception {
        respond(200, body);
        assertFailure(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative-distance", "missing-duration", "string-duration", "fraction-duration", "invalid-coordinate", "string-coordinate"})
    void rejectsCorruptSummaryAndGeometry(String mutation) throws Exception {
        String body = switch (mutation) {
            case "negative-distance" -> validBody(1).replace("12500", "-1");
            case "missing-duration" -> validBody(1).replace(",\"duration\":1", "");
            case "string-duration" -> validBody(1).replace("\"duration\":1", "\"duration\":\"1\"");
            case "fraction-duration" -> validBody(1).replace("\"duration\":1", "\"duration\":1.5");
            case "invalid-coordinate" -> validBody(1).replace("126.97", "200.0");
            default -> validBody(1).replace("126.97", "\"126.97\"");
        };
        respond(200, body);
        assertFailure(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
    }

    @Test
    void doesNotCallProviderWhenDisabledOrCredentialsMissing() {
        for (var api : new NaverDirectionsClient[]{api(false, "test-secret"), api(true, " ")}) {
            assertThatThrownBy(() -> api.findRoute(ORIGIN, DESTINATION)).isInstanceOf(MapException.class);
        }
        assertThat(metrics.getMeters()).isEmpty();
        verifyNoInteractions(transport);
    }

    @Test
    void protectsCredentialsAndRejectsUnsafeConfiguration() {
        var properties = properties(true, "test-secret");
        assertThat(properties.toString()).doesNotContain("test-secret", "test-id");
        assertThatThrownBy(() -> new NaverDirectionsClient.Properties(true, "id", "secret", "http://maps.test", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NaverDirectionsClient.Properties(true, "id", "secret", null, Duration.ZERO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void respond(int status, String body) throws IOException {
        when(transport.get(ORIGIN, DESTINATION)).thenReturn(
                new NaverDirectionsTransport.ProviderResponse(status, body.getBytes(StandardCharsets.UTF_8)));
    }

    private String validBody(long milliseconds) {
        return "{\"code\":0,\"route\":{\"traoptimal\":[{\"summary\":{\"distance\":12500,\"duration\":" + milliseconds
                + "},\"path\":[[126.97,37.56],[127.01,37.52],[127.02,37.49]]}]}}";
    }

    private NaverDirectionsClient api(boolean enabled, String secret) {
        return new NaverDirectionsClient(transport, properties(enabled, secret), new ObjectMapper(), metrics);
    }

    private NaverDirectionsClient.Properties properties(boolean enabled, String secret) {
        return new NaverDirectionsClient.Properties(enabled, "test-id", secret, "https://maps.test", null, null, null);
    }

    private void assertFailure(MapErrorCode expected) {
        assertThatThrownBy(() -> api(true, "test-secret").findRoute(ORIGIN, DESTINATION))
                .isInstanceOfSatisfying(MapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(expected);
                    assertThat(exception.getMessage()).doesNotContain("private", "test-secret");
                });
    }
}
