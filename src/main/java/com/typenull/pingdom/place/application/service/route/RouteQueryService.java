package com.typenull.pingdom.place.application.service.route;

import com.typenull.pingdom.place.api.dto.route.RouteRequest;
import com.typenull.pingdom.place.api.dto.route.RouteResponse;
import com.typenull.pingdom.place.infrastructure.route.NaverDirectionsClient;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 입력과 지원 이동수단을 검증하고 공급자 조회에 위임합니다. 경로 저장·대체 경로 생성은 하지 않습니다. */
@Service
public class RouteQueryService {
    private static final Set<String> UNSUPPORTED_MODES = Set.of("walk", "transit", "bike");
    private final NaverDirectionsClient client;

    public RouteQueryService(NaverDirectionsClient client) {
        this.client = client;
    }

    public RouteResponse findRoute(RouteRequest request) {
        if (request == null || request.origin() == null || request.destination() == null
                || !request.origin().isValid() || !request.destination().isValid() || request.mode() == null
                || (request.origin().latitude().doubleValue() == request.destination().latitude().doubleValue()
                && request.origin().longitude().doubleValue() == request.destination().longitude().doubleValue())) {
            throw new MapException(MapErrorCode.INVALID_ROUTE_REQUEST);
        }
        if (UNSUPPORTED_MODES.contains(request.mode())) {
            throw new MapException(MapErrorCode.UNSUPPORTED_ROUTE_MODE);
        }
        if (!"car".equals(request.mode())) {
            throw new MapException(MapErrorCode.INVALID_ROUTE_REQUEST);
        }
        return client.findRoute(request.origin(), request.destination());
    }
}
