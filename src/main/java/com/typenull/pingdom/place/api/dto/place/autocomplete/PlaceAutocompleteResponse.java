package com.typenull.pingdom.place.api.dto.place.autocomplete;

import java.util.List;

public record PlaceAutocompleteResponse(
        String keyword,
        int limit,
        int totalCount,
        List<PlaceAutocompleteItem> places
) {
}
