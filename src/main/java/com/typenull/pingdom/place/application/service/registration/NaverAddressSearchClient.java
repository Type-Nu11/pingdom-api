package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NaverAddressSearchClient {
    private static final int MAX_RESULTS = 10;

    private final RestClient restClient;
    private final Properties properties;

    public NaverAddressSearchClient(
            @Qualifier("naverAddressSearchRestClient") RestClient naverAddressSearchRestClient,
            Properties properties
    ) {
        this.properties = properties;
        this.restClient = naverAddressSearchRestClient;
    }

    public JsonNode search(String query) {
        if (!properties.isConfigured()) {
            throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_UNAVAILABLE);
        }
        try {
            JsonNode response = restClient.get()
                    .uri(uri -> uri.path("/map-geocode/v2/geocode")
                            .queryParam("query", query)
                            .queryParam("page", 1)
                            .queryParam("count", MAX_RESULTS)
                            .build())
                    .header("x-ncp-apigw-api-key-id", properties.clientId())
                    .header("x-ncp-apigw-api-key", properties.clientSecret())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !"OK".equals(response.path("status").asText())) {
                throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_FAILED);
            }
            return response.path("addresses");
        } catch (MapException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 429) {
                throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_RATE_LIMITED);
            }
            if (exception.getStatusCode().is5xxServerError()) {
                throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_UNAVAILABLE);
            }
            throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_FAILED);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_TIMEOUT);
            }
            throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_FAILED);
        }
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @ConfigurationProperties(prefix = "place.registration.naver-geocoding")
    public record Properties(
            Boolean enabled,
            String clientId,
            String clientSecret,
            String baseUrl,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        public Properties {
            enabled = Boolean.TRUE.equals(enabled);
            clientId = normalize(clientId);
            clientSecret = normalize(clientSecret);
            baseUrl = normalize(baseUrl);
            if (baseUrl == null) {
                baseUrl = "https://naveropenapi.apigw.ntruss.com";
            }
            if (connectTimeout == null || !connectTimeout.isPositive()) {
                connectTimeout = Duration.ofSeconds(2);
            }
            if (readTimeout == null || !readTimeout.isPositive()) {
                readTimeout = Duration.ofSeconds(3);
            }
        }

        public boolean isConfigured() {
            return enabled && clientId != null && clientSecret != null;
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
