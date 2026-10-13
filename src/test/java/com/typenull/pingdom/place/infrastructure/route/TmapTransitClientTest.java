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
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TmapTransitClientTest {
    private static final RouteCoordinate START = new RouteCoordinate(37.555162, 126.936928);
    private static final RouteCoordinate END = new RouteCoordinate(37.564436, 127.029281);

    @Test
    void mapsItineraryLegsFareStationsAndPath() throws Exception {
        TmapTransitTransport transport = mock(TmapTransitTransport.class);
        String body = """
                {"metaData":{"plan":{"itineraries":[{"totalTime":1229,"totalDistance":9000,
                "transferCount":1,"totalWalkDistance":128,"totalWalkTime":114,
                "fare":{"regular":{"totalFare":1400}},"legs":[{"mode":"SUBWAY","distance":8500,
                "sectionTime":1000,"route":"2호선","routeId":"2","routeColor":"00A84D","type":1,
                "start":{"name":"신촌","lon":126.93,"lat":37.55},"end":{"name":"상왕십리","lon":127.02,"lat":37.56},
                "passShape":{"linestring":"126.93,37.55 127.02,37.56"},
                "passStopList":{"stations":[{"stationName":"신촌","stationID":"S1","lon":"126.93","lat":"37.55"}]}}]}]}}}
                """;
        when(transport.post(START, END)).thenReturn(
                new TmapTransitTransport.ProviderResponse(200, body.getBytes(StandardCharsets.UTF_8)));
        var client = new TmapTransitClient(transport, properties(true), new ObjectMapper(), new SimpleMeterRegistry());

        var response = client.findRoute(START, END);

        assertThat(response.provider()).isEqualTo("tmap");
        assertThat(response.itineraries()).hasSize(1);
        assertThat(response.itineraries().getFirst().totalFare()).isEqualTo(1400);
        assertThat(response.itineraries().getFirst().legs().getFirst().path()).hasSize(2);
        assertThat(response.itineraries().getFirst().legs().getFirst().stations().getFirst().stationId()).isEqualTo("S1");
    }

    @Test
    void disabledProviderDoesNotCallTransport() {
        TmapTransitTransport transport = mock(TmapTransitTransport.class);
        var client = new TmapTransitClient(transport, properties(false), new ObjectMapper(), new SimpleMeterRegistry());

        assertThatThrownBy(() -> client.findRoute(START, END)).isInstanceOfSatisfying(MapException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.ROUTE_PROVIDER_UNAVAILABLE));
        verifyNoInteractions(transport);
    }

    @Test
    void emptyPlanIsRouteNotFound() throws Exception {
        TmapTransitTransport transport = mock(TmapTransitTransport.class);
        when(transport.post(START, END)).thenReturn(new TmapTransitTransport.ProviderResponse(200,
                "{\"metaData\":{\"plan\":{\"itineraries\":[]}}}".getBytes(StandardCharsets.UTF_8)));
        var client = new TmapTransitClient(transport, properties(true), new ObjectMapper(), new SimpleMeterRegistry());

        assertThatThrownBy(() -> client.findRoute(START, END)).isInstanceOfSatisfying(MapException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.ROUTE_NOT_FOUND));
    }

    private TmapTransitClient.Properties properties(boolean enabled) {
        return new TmapTransitClient.Properties(enabled, "secret-key", "https://apis.openapi.sk.com", null, null, null);
    }
}
