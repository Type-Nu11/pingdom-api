package com.typenull.pingdom.place.application.service.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.typenull.pingdom.place.api.dto.registration.NaverAddressSearchResponse;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;

@Service
public class NaverAddressSearchService {
    private static final int MAX_RESULTS = 10;

    private final NaverAddressSearchClient client;

    public NaverAddressSearchService(NaverAddressSearchClient client) {
        this.client = client;
    }

    public NaverAddressSearchResponse search(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            throw new MapException(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID);
        }
        JsonNode addresses = client.search(normalized);
        List<NaverAddressSearchResponse.Item> items = addresses.isArray()
                ? StreamSupport.stream(addresses.spliterator(), false).limit(MAX_RESULTS).map(this::item).toList()
                : List.of();
        return new NaverAddressSearchResponse(items);
    }

    private NaverAddressSearchResponse.Item item(JsonNode address) {
        try {
            return new NaverAddressSearchResponse.Item(
                    address.path("roadAddress").asText(),
                    address.path("jibunAddress").asText(),
                    postalCode(address.path("addressElements")),
                    Double.parseDouble(address.path("y").asText()),
                    Double.parseDouble(address.path("x").asText())
            );
        } catch (NumberFormatException exception) {
            throw new MapException(MapErrorCode.NAVER_ADDRESS_SEARCH_FAILED);
        }
    }

    private String postalCode(JsonNode addressElements) {
        if (!addressElements.isArray()) {
            return null;
        }
        for (JsonNode element : addressElements) {
            if (hasPostalCodeType(element.path("types"))) {
                String postalCode = element.path("longName").asText();
                return postalCode.isBlank() ? null : postalCode;
            }
        }
        return null;
    }

    private boolean hasPostalCodeType(JsonNode types) {
        if (!types.isArray()) {
            return false;
        }
        for (JsonNode type : types) {
            if ("POSTAL_CODE".equals(type.asText())) {
                return true;
            }
        }
        return false;
    }
}
