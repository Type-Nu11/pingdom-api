package com.typenull.pingdom.place.application.service.route;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.place.api.dto.route.RouteRequest;
import com.typenull.pingdom.place.infrastructure.route.NaverDirectionsClient;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class RouteQueryServiceTest {
    private final NaverDirectionsClient client = mock(NaverDirectionsClient.class);
    private final RouteQueryService service = new RouteQueryService(client);
    private static final RouteCoordinate START = new RouteCoordinate(37.5, 127.0);
    private static final RouteCoordinate END = new RouteCoordinate(37.6, 127.1);

    @Test
    void delegatesOnlyCarToProvider() {
        service.findRoute(new RouteRequest(START, END, "car"));
        verify(client).findRoute(START, END);
    }

    @ParameterizedTest
    @ValueSource(strings = {"walk", "transit", "bike"})
    void neverFallsBackToCarForUnsupportedMode(String mode) {
        assertFailure(new RouteRequest(START, END, mode), MapErrorCode.UNSUPPORTED_ROUTE_MODE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CAR", "flying", "1"})
    void rejectsUnknownMode(String mode) {
        assertFailure(new RouteRequest(START, END, mode), MapErrorCode.INVALID_ROUTE_REQUEST);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidCoordinatesBeforeExternalCall(RouteRequest request) {
        assertFailure(request, MapErrorCode.INVALID_ROUTE_REQUEST);
    }

    static Stream<RouteRequest> invalidRequests() {
        return Stream.of(null, new RouteRequest(null, END, "car"), new RouteRequest(START, null, "car"),
                new RouteRequest(START, END, null), new RouteRequest(START, START, "car"),
                new RouteRequest(new RouteCoordinate(null, 127.0), END, "car"),
                new RouteRequest(new RouteCoordinate(37.5, null), END, "car"),
                new RouteRequest(new RouteCoordinate(90.1, 127.0), END, "car"),
                new RouteRequest(new RouteCoordinate(-90.1, 127.0), END, "car"),
                new RouteRequest(new RouteCoordinate(37.5, 180.1), END, "car"),
                new RouteRequest(new RouteCoordinate(37.5, -180.1), END, "car"),
                new RouteRequest(new RouteCoordinate(Double.NaN, 127.0), END, "car"),
                new RouteRequest(new RouteCoordinate(37.5, Double.POSITIVE_INFINITY), END, "car"),
                new RouteRequest(new RouteCoordinate(-0.0, 0.0), new RouteCoordinate(0.0, -0.0), "car"));
    }

    private void assertFailure(RouteRequest request, MapErrorCode code) {
        assertThatThrownBy(() -> service.findRoute(request)).isInstanceOfSatisfying(MapException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode()).isEqualTo(code));
        verifyNoInteractions(client);
    }
}
