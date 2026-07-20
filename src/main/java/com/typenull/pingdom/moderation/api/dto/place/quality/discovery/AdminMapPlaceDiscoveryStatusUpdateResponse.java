package com.typenull.pingdom.moderation.api.dto.place.quality.discovery;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;

public record AdminMapPlaceDiscoveryStatusUpdateResponse(
        Long placeId,
        PlaceDiscoveryStatus discoveryStatus,
        String message
) {
}
