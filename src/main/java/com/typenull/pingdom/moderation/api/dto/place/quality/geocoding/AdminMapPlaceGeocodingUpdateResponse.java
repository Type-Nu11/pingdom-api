package com.typenull.pingdom.moderation.api.dto.place.quality.geocoding;

import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;

public record AdminMapPlaceGeocodingUpdateResponse(
        Long placeId,
        String address,
        String roadAddress,
        String jibunAddress,
        String postalCode,
        GeocodingSource geocodingSource,
        Double latitude,
        Double longitude,
        String message
) {
}
