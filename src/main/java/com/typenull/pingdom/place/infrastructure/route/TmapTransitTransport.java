package com.typenull.pingdom.place.infrastructure.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import java.io.IOException;
import java.net.SocketTimeoutException;
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
class TmapTransitTransport {
    private final CloseableHttpAsyncClient client;
    private final TmapTransitClient.Properties properties;
    private final ObjectMapper mapper;

    TmapTransitTransport(@Qualifier("tmapTransitHttpClient") CloseableHttpAsyncClient client,
            TmapTransitClient.Properties properties, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.mapper = mapper;
    }

    ProviderResponse post(RouteCoordinate origin, RouteCoordinate destination) throws IOException {
        var body = requestBody(origin, destination);
        var request = SimpleRequestBuilder.post(properties.baseUrl() + "/transit/routes")
                .setHeader("appKey", properties.appKey()).setHeader("Accept", "application/json")
                .setBody(body, ContentType.APPLICATION_JSON).build();
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
            throw new SocketTimeoutException("대중교통 공급자 전체 응답 제한 시간 초과");
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("대중교통 공급자 요청 중단", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException cause) throw cause;
            throw new IOException("대중교통 공급자 통신 실패", exception.getCause());
        }
    }

    private String requestBody(RouteCoordinate origin, RouteCoordinate destination) throws JsonProcessingException {
        return mapper.writeValueAsString(Map.of(
                "startX", origin.longitude().toString(), "startY", origin.latitude().toString(),
                "endX", destination.longitude().toString(), "endY", destination.latitude().toString(),
                "count", 10, "lang", 0, "format", "json"));
    }

    record ProviderResponse(int status, byte[] body) { }
}
