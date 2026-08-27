package com.typenull.pingdom.place.infrastructure.localhot;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoPlaceAdministrativeRegionResolver implements PlaceAdministrativeRegionResolver {

    private final RestClient restClient;
    private final KakaoLocalRegionProperties properties;
    private final ConcurrentHashMap<String, CachedRegion> cache = new ConcurrentHashMap<>();

    public KakaoPlaceAdministrativeRegionResolver(
            @Qualifier("kakaoLocalRegionRestClient") RestClient kakaoLocalRegionRestClient,
            KakaoLocalRegionProperties properties
    ) {
        this.restClient = kakaoLocalRegionRestClient;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public ResolvedPlaceAdministrativeRegion resolve(double latitude, double longitude) {
        if (!isConfigured()) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE);
        }

        String cacheKey = cacheKey(latitude, longitude);
        CachedRegion cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.region();
        }

        ResolvedPlaceAdministrativeRegion resolved = requestRegion(latitude, longitude);
        if (cache.size() >= properties.cacheMaxEntries()) {
            cache.clear();
        }
        cache.put(cacheKey, new CachedRegion(resolved, Instant.now().plus(properties.cacheTtl())));
        return resolved;
    }

    private ResolvedPlaceAdministrativeRegion requestRegion(double latitude, double longitude) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2regioncode.json")
                            .queryParam("x", longitude)
                            .queryParam("y", latitude)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.apiKey())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode legalRegion = null;
            if (response != null) {
                for (JsonNode document : response.path("documents")) {
                    if ("B".equals(document.path("region_type").asText())) {
                        legalRegion = document;
                        break;
                    }
                }
            }
            if (legalRegion == null) {
                throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
            }

            String legalDongCode = legalRegion.path("code").asText();
            String sido = legalRegion.path("region_1depth_name").asText();
            String sigungu = legalRegion.path("region_2depth_name").asText();
            if (legalDongCode.length() < 5 || sido.isBlank() || sigungu.isBlank()) {
                throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
            }
            return new ResolvedPlaceAdministrativeRegion(
                    legalDongCode.substring(0, 5),
                    sido,
                    sigungu,
                    sido + " " + sigungu
            );
        } catch (MapException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED);
        }
    }

    private String cacheKey(double latitude, double longitude) {
        return Math.round(latitude * 1_000_000d) + ":" + Math.round(longitude * 1_000_000d);
    }

    private record CachedRegion(ResolvedPlaceAdministrativeRegion region, Instant expiresAt) {
    }
}
