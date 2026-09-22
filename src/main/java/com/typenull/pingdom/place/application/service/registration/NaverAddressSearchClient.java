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

/** 서버 전용 Naver Geocoding 인증값으로 주소 후보 원본을 조회하고 외부 장애를 지도 오류로 변환합니다. */
@Component
public class NaverAddressSearchClient {
    private static final int MAX_RESULTS = 10;

    private final RestClient restClient;
    private final Properties properties;

    /** 주소 검색 전용 RestClient와 환경 설정을 주입받아 다른 Naver 연동과 인증·timeout을 분리합니다. */
    public NaverAddressSearchClient(
            @Qualifier("naverAddressSearchRestClient") RestClient naverAddressSearchRestClient,
            Properties properties
    ) {
        this.properties = properties;
        this.restClient = naverAddressSearchRestClient;
    }

    /**
     * 검색어로 최대 10개의 Geocoding 후보를 조회합니다.
     * 인증값이 없으면 호출하지 않고, 429·timeout·서버 장애는 각각의 공통 오류 코드로 구분합니다.
     */
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

    /** 네트워크 예외의 원인 체인에 socket timeout이 있는지 확인합니다. */
    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** 주소 검색 활성화 여부, 서버 전용 인증값, 외부 API endpoint와 timeout 설정입니다. */
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

        /** 활성화 상태와 두 인증값이 모두 있을 때만 외부 API 호출을 허용합니다. */
        public boolean isConfigured() {
            return enabled && clientId != null && clientSecret != null;
        }

        /** 빈 환경 변수는 유효한 인증값이나 URL로 사용하지 않도록 null로 정규화합니다. */
        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
