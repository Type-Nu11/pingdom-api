package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.place.api.dto.registration.NaverPlaceSearchResponse;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import org.springframework.stereotype.Service;

@Service
public class NaverPlaceSearchService {
    private final NaverPlaceSearchClient client;

    public NaverPlaceSearchService(NaverPlaceSearchClient client) { this.client = client; }

    public NaverPlaceSearchResponse search(String query, Long userId) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        JsonNode items = client.search(normalized);
        return new NaverPlaceSearchResponse(items.isArray() ? java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .limit(5).map(this::item).toList() : java.util.List.of());
    }

    private NaverPlaceSearchResponse.Item item(JsonNode node) {
        try {
            return new NaverPlaceSearchResponse.Item(node.path("title").asText().replaceAll("<[^>]*>", ""),
                    node.path("roadAddress").asText(), node.path("address").asText(),
                    Double.parseDouble(node.path("mapy").asText()) / 10_000_000d,
                    Double.parseDouble(node.path("mapx").asText()) / 10_000_000d);
        } catch (NumberFormatException exception) { throw new MapException(MapErrorCode.NAVER_PLACE_SEARCH_FAILED); }
    }
}
