package com.typenull.pingdom.place.infrastructure.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.shared.config.proxy.OiletProxyProperties;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
class TmapPedestrianTransport {
    private final CloseableHttpAsyncClient client;
    private final TmapPedestrianClient.Properties properties;
    private final ObjectMapper mapper;
    private final OiletProxyProperties oiletProxy;

    TmapPedestrianTransport(@Qualifier("tmapPedestrianHttpClient") CloseableHttpAsyncClient client,
            TmapPedestrianClient.Properties properties, ObjectMapper mapper,
            OiletProxyProperties oiletProxy) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
        this.oiletProxy = oiletProxy;
    }

    ProviderResponse post(RouteCoordinate origin, RouteCoordinate destination) throws IOException {
        var request = SimpleRequestBuilder.post(oiletProxy.targetUrl(properties.baseUrl()) + "/tmap/routes/pedestrian?version=1")
                .setHeader("appKey", properties.appKey()).setHeader("Accept", "application/json")
                .setBody(requestBody(origin, destination), ContentType.APPLICATION_JSON).build();
        long started = System.nanoTime();
        var response = client.execute(request, null);
        try {
            long remaining = properties.requestTimeout().toNanos() - (System.nanoTime() - started);
            if (remaining <= 0) throw new TimeoutException();
            var completed = response.get(remaining, TimeUnit.NANOSECONDS);
            byte[] bytes = completed.getBodyBytes();
            return new ProviderResponse(completed.getCode(), bytes == null ? new byte[0] : bytes);
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw new SocketTimeoutException("보행자 공급자 전체 응답 제한 시간 초과");
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("보행자 공급자 요청 중단", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException cause) throw cause;
            throw new IOException("보행자 공급자 통신 실패", exception.getCause());
        }
    }

    private String requestBody(RouteCoordinate origin, RouteCoordinate destination) throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", origin.longitude());
        body.put("startY", origin.latitude());
        body.put("endX", destination.longitude());
        body.put("endY", destination.latitude());
        body.put("startName", "%EC%B6%9C%EB%B0%9C");
        body.put("endName", "%EB%8F%84%EC%B0%A9");
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", "0");
        body.put("sort", "index");
        return mapper.writeValueAsString(body);
    }

    record ProviderResponse(int status, byte[] body) { }
}
