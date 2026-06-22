package com.typenull.pingdom.place.api.dto.place;

import java.util.List;

public record PlaceAutocompleteResponse(
        String keyword,
        int limit,
        int totalCount,
        List<PlaceAutocompleteItem> places
) {
}
