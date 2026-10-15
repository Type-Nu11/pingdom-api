package com.typenull.pingdom.place.infrastructure.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TmapPedestrianClientTest {
    private static final RouteCoordinate START = new RouteCoordinate(37.55677, 126.92365);
    private static final RouteCoordinate END = new RouteCoordinate(37.55279, 126.92432);

    @Test
    void mapsSummaryPathAndWalkingSteps() throws Exception {
        TmapPedestrianTransport transport = mock(TmapPedestrianTransport.class);
        String body = """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","geometry":{"type":"Point","coordinates":[126.92365,37.55677]},
                   "properties":{"totalDistance":320,"totalTime":260,"description":"출발"}},
                  {"type":"Feature","geometry":{"type":"LineString","coordinates":[[126.92365,37.55677],[126.924,37.555]]},
                   "properties":{"description":"보행자도로를 따라 이동","distance":180,"time":140}},
                  {"type":"Feature","geometry":{"type":"LineString","coordinates":[[126.924,37.555],[126.92432,37.55279]]},
                   "properties":{"description":"목적지까지 직진","distance":140,"time":120}}]}
                """;
        when(transport.post(START, END)).thenReturn(
                new TmapPedestrianTransport.ProviderResponse(200, body.getBytes(StandardCharsets.UTF_8)));
        var client = new TmapPedestrianClient(transport, properties(true), new ObjectMapper(), new SimpleMeterRegistry());

        var response = client.findRoute(START, END);

        assertThat(response.mode()).isEqualTo("walk");
        assertThat(response.provider()).isEqualTo("tmap");
        assertThat(response.distanceMeters()).isEqualTo(320);
        assertThat(response.durationSeconds()).isEqualTo(260);
        assertThat(response.path()).hasSize(3);
        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().getFirst().instruction()).isEqualTo("보행자도로를 따라 이동");
    }

    @Test
    void disabledProviderDoesNotCallTransport() {
        TmapPedestrianTransport transport = mock(TmapPedestrianTransport.class);
        var client = new TmapPedestrianClient(transport, properties(false), new ObjectMapper(), new SimpleMeterRegistry());

        assertError(client, MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
        verifyNoInteractions(transport);
    }

    @Test
    void emptyFeaturesIsRouteNotFound() throws Exception {
        TmapPedestrianTransport transport = mock(TmapPedestrianTransport.class);
        when(transport.post(START, END)).thenReturn(new TmapPedestrianTransport.ProviderResponse(200,
                "{\"features\":[]}".getBytes(StandardCharsets.UTF_8)));
        var client = new TmapPedestrianClient(transport, properties(true), new ObjectMapper(), new SimpleMeterRegistry());

        assertError(client, MapErrorCode.ROUTE_NOT_FOUND);
    }

    @Test
    void invalidSuccessfulResponseIsUnavailable() throws Exception {
        TmapPedestrianTransport transport = mock(TmapPedestrianTransport.class);
        when(transport.post(START, END)).thenReturn(new TmapPedestrianTransport.ProviderResponse(200,
                "{\"features\":[{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[126.9,37.5]]},\"properties\":{}}]}"
                        .getBytes(StandardCharsets.UTF_8)));
        var client = new TmapPedestrianClient(transport, properties(true), new ObjectMapper(), new SimpleMeterRegistry());

        assertError(client, MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE);
    }

    @Test
    void providerStatusAndTimeoutAreNormalized() throws Exception {
        TmapPedestrianTransport transport = mock(TmapPedestrianTransport.class);
        when(transport.post(START, END)).thenReturn(new TmapPedestrianTransport.ProviderResponse(404, new byte[0]));
        var client = new TmapPedestrianClient(transport, properties(true), new ObjectMapper(), new SimpleMeterRegistry());
        assertError(client, MapErrorCode.ROUTE_NOT_FOUND);

        when(transport.post(START, END)).thenThrow(new SocketTimeoutException());
        assertError(client, MapErrorCode.ROUTE_PROVIDER_TIMEOUT);
    }

    private void assertError(TmapPedestrianClient client, MapErrorCode code) {
        assertThatThrownBy(() -> client.findRoute(START, END)).isInstanceOfSatisfying(MapException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    private TmapPedestrianClient.Properties properties(boolean enabled) {
        return new TmapPedestrianClient.Properties(enabled, "secret-key", "https://apis.openapi.sk.com", null, null, null);
    }
}
