package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 네이버 지역 검색을 인증 헤더와 연결·읽기 제한 시간으로 호출.
 * 미설정은 이용 불가, HTTP 통신 실패나 빈 본문은 검색 실패로 변환하며 응답의 items만 전달.
 */
@Component
public class NaverPlaceSearchClient {
    private final RestClient restClient;
    private final Properties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NaverPlaceSearchClient(
            @Qualifier("naverPlaceSearchRestClient") RestClient naverPlaceSearchRestClient,
            Properties properties
    ) {
        this.properties = properties;
        this.restClient = naverPlaceSearchRestClient;
    }

    /**
     * 설정된 인증 헤더로 네이버 지역 검색에 최대 5건을 요청하고 응답의 items 노드를 반환.
     * 설정 누락은 사용 불가, 빈 응답·HTTP 클라이언트 실패는 검색 실패로 변환. 검색어와 items 구조 검증은 호출 측 책임이며 자동 재시도는 없음.
     */
    public JsonNode search(String query) {
        if (!properties.isConfigured()) throw new MapException(MapErrorCode.NAVER_PLACE_SEARCH_UNAVAILABLE);
        try {
            byte[] body = restClient.get().uri(uri -> uri.path("/search/v1/local")
                    .queryParam("query", query).queryParam("display", 5)
                    .queryParam("format", "json").build())
                    .header("X-NCP-APIGW-API-KEY-ID", properties.clientId())
                    .header("X-NCP-APIGW-API-KEY", properties.clientSecret())
                    .retrieve().body(byte[].class);
            // API HUB가 text/plain으로 반환하는 JSON도 UTF-8 원본 바이트로 파싱.
            if (body == null || body.length == 0) throw new MapException(MapErrorCode.NAVER_PLACE_SEARCH_FAILED);
            JsonNode response = objectMapper.readTree(body);
            if (response == null || response.isMissingNode() || response.isNull()) throw new MapException(MapErrorCode.NAVER_PLACE_SEARCH_FAILED);
            return response.path("items");
        } catch (MapException exception) { throw exception;
        } catch (RestClientException | IOException exception) { throw new MapException(MapErrorCode.NAVER_PLACE_SEARCH_FAILED); }
    }

    @ConfigurationProperties(prefix = "place.registration.naver-search")
    public record Properties(Boolean enabled, String clientId, String clientSecret, String baseUrl,
                             Duration connectTimeout, Duration readTimeout) {
        public Properties {
            enabled = Boolean.TRUE.equals(enabled);
            clientId = normalized(clientId); clientSecret = normalized(clientSecret);
            baseUrl = normalized(baseUrl); if (baseUrl == null) baseUrl = "https://naverapihub.apigw.ntruss.com";
            if (connectTimeout == null || !connectTimeout.isPositive()) connectTimeout = Duration.ofSeconds(2);
            if (readTimeout == null || !readTimeout.isPositive()) readTimeout = Duration.ofSeconds(3);
        }
        public boolean isConfigured() { return enabled && clientId != null && clientSecret != null; }
        private static String normalized(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    }
}
