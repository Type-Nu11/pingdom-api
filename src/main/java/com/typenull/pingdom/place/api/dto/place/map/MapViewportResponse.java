package com.typenull.pingdom.place.api.dto.place.map;

import java.util.List;

public record MapViewportResponse(
        String mode,
        int zoom,
        List<MapClusterItem> clusters,
        List<MapMarkerItem> markers,
        boolean truncated
) {
}
