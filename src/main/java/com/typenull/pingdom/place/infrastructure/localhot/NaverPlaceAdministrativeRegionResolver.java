package com.typenull.pingdom.place.infrastructure.localhot;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "place.local-hot.naver", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NaverPlaceAdministrativeRegionResolver implements PlaceAdministrativeRegionResolver {
    @Qualifier("naverLocalRegionRestClient")
    private final RestClient restClient;
    private final NaverLocalRegionProperties properties;

    @Override
    public boolean isConfigured() { return properties.isConfigured(); }

    @Override
    public ResolvedPlaceAdministrativeRegion resolve(double latitude, double longitude) {
        if (!isConfigured()) throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE);
        try {
            JsonNode response = restClient.get()
                    .uri(uri -> uri.path("/map-reversegeocode/v2/gc")
                            .queryParam("request", "coordsToaddr")
                            .queryParam("coords", longitude + "," + latitude)
                            .queryParam("sourcecrs", "epsg:4326")
                            .queryParam("orders", "legalcode")
                            .queryParam("output", "json").build())
                    .header("x-ncp-apigw-api-key-id", properties.clientId())
                    .header("x-ncp-apigw-api-key", properties.clientSecret())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve().body(JsonNode.class);
            return toRegion(response);
        } catch (MapException e) { throw e;
        } catch (RestClientException e) { throw new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED); }
    }

    private ResolvedPlaceAdministrativeRegion toRegion(JsonNode response) {
        JsonNode result = response == null ? null : findLegalCode(response.path("results"));
        if (result == null) throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
        String code = result.path("code").path("id").asText();
        String sido = result.path("region").path("area1").path("name").asText();
        String sigungu = result.path("region").path("area2").path("name").asText();
        if (code.length() < 5 || sido.isBlank() || sigungu.isBlank()) {
            throw new MapException(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND);
        }
        return new ResolvedPlaceAdministrativeRegion(code.substring(0, 5), sido, sigungu, sido + " " + sigungu);
    }

    private JsonNode findLegalCode(JsonNode results) {
        if (!results.isArray()) return null;
        for (JsonNode result : results) if ("legalcode".equals(result.path("name").asText())) return result;
        return null;
    }
}
