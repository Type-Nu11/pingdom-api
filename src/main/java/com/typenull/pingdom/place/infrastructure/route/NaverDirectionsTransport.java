package com.typenull.pingdom.place.infrastructure.route;

import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** 연결 풀 대기부터 전체 본문 수신까지 제한하며 취소 시 실제 HTTP 교환을 중단합니다. */
@Component
class NaverDirectionsTransport {
    private final CloseableHttpAsyncClient client;
    private final NaverDirectionsClient.Properties properties;

    NaverDirectionsTransport(@Qualifier("naverDirectionsHttpClient") CloseableHttpAsyncClient client,
            NaverDirectionsClient.Properties properties) {
        this.client = client;
        this.properties = properties;
    }

    ProviderResponse get(RouteCoordinate origin, RouteCoordinate destination) throws IOException {
        var uri = UriComponentsBuilder.fromUriString(properties.baseUrl()).path("/map-direction/v1/driving")
                .queryParam("start", origin.longitude() + "," + origin.latitude())
                .queryParam("goal", destination.longitude() + "," + destination.latitude())
                .queryParam("option", "traoptimal").build().toUri();
        var request = SimpleRequestBuilder.get(uri)
                .setHeader("x-ncp-apigw-api-key-id", properties.clientId())
                .setHeader("x-ncp-apigw-api-key", properties.clientSecret())
                .setHeader("Accept", "application/json").build();
        long started = System.nanoTime();
        var response = client.execute(request, null);
        try {
            long remaining = properties.requestTimeout().toNanos() - (System.nanoTime() - started);
            if (remaining <= 0) {
                throw new TimeoutException();
            }
            // SimpleHttpResponse의 Future는 헤더뿐 아니라 전체 본문 수신 후 완료됩니다.
            var completed = response.get(remaining, TimeUnit.NANOSECONDS);
            byte[] body = completed.getBodyBytes();
            return new ProviderResponse(completed.getCode(), body == null ? new byte[0] : body);
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw new SocketTimeoutException("경로 공급자 전체 응답 제한 시간 초과");
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("경로 공급자 요청 중단", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException cause) {
                throw cause;
            }
            throw new IOException("경로 공급자 통신 실패", exception.getCause());
        }
    }

    record ProviderResponse(int status, byte[] body) {}
}
