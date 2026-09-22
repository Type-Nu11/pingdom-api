package com.typenull.pingdom.place.infrastructure.localhot;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Naver legalcode 결과를 시·군·구로 변환하고 프로세스 내부 TTL 캐시에 보관합니다.
 * 네이버 사용 설정이 켜진 경우 선택되며 인증 정보 누락이나 요청 실패 시 Kakao로 자동 전환하지 않습니다.
 */
@Component
@ConditionalOnProperty(prefix = "place.local-hot.naver", name = "enabled", havingValue = "true")
public class NaverPlaceAdministrativeRegionResolver implements PlaceAdministrativeRegionResolver {
    private final RestClient restClient;
    private final NaverLocalRegionProperties properties;
    private final ConcurrentHashMap<String, CachedRegion> cache = new ConcurrentHashMap<>();

    public NaverPlaceAdministrativeRegionResolver(
            @Qualifier("naverLocalRegionRestClient") RestClient naverLocalRegionRestClient,
            NaverLocalRegionProperties properties
    ) {
        this.restClient = naverLocalRegionRestClient;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public ResolvedPlaceAdministrativeRegion resolve(double latitude, double longitude) {
        if (!isConfigured()) throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE);
        String cacheKey = cacheKey(latitude, longitude);
        CachedRegion cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.region();
        try {
            JsonNode response = restClient.get()
                    .uri(uri -> uri.path("/map-reversegeocode/v2/gc")
                            .queryParam("request", "coordsToaddr")
                            .queryParam("coords", longitude + "," + latitude)
                            .queryParam("sourcecrs", "epsg:4326")
                            .queryParam("orders", "legalcode")
                            .queryParam("output", "json")
                            .build())
                    .header("x-ncp-apigw-api-key-id", properties.clientId())
                    .header("x-ncp-apigw-api-key", properties.clientSecret())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve().body(JsonNode.class);
            ResolvedPlaceAdministrativeRegion region = toRegion(response);
            if (cache.size() >= properties.cacheMaxEntries()) cache.clear();
            cache.put(cacheKey, new CachedRegion(region, Instant.now().plus(properties.cacheTtl())));
            return region;
        } catch (MapException e) { throw e;
        } catch (RestClientException e) { throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED); }
    }

    private ResolvedPlaceAdministrativeRegion toRegion(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED);
        }

        int statusCode = response.path("status").path("code").asInt(-1);
        if (statusCode == 3) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
        }
        if (statusCode != 0) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED);
        }

        JsonNode result = findLegalCode(response.path("results"));
        if (result == null) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
        }
        String code = result.path("code").path("id").asText();
        String sido = result.path("region").path("area1").path("name").asText();
        String sigungu = result.path("region").path("area2").path("name").asText();
        if (!code.matches("\\d{10}") || sido.isBlank()) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
        }

        // 세종특별자치시는 네이버 응답의 area2가 비어 있으므로 시·도명을 지역 표시와 시군구 값으로 사용한다.
        if (sigungu.isBlank()) {
            if (!"세종특별자치시".equals(sido)) {
                throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
            }
            return new ResolvedPlaceAdministrativeRegion(code.substring(0, 5), sido, sido, sido);
        }
        return new ResolvedPlaceAdministrativeRegion(code.substring(0, 5), sido, sigungu, sido + " " + sigungu);
    }

    private JsonNode findLegalCode(JsonNode results) {
        if (!results.isArray()) {
            return null;
        }
        for (JsonNode result : results) {
            if ("legalcode".equals(result.path("name").asText())) {
                return result;
            }
        }
        return null;
    }

    private String cacheKey(double latitude, double longitude) {
        return Math.round(latitude * 1_000_000d) + ":" + Math.round(longitude * 1_000_000d);
    }

    private record CachedRegion(ResolvedPlaceAdministrativeRegion region, Instant expiresAt) {}
}
