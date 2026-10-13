package com.typenull.pingdom.place.api.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "TMAP 대중교통 추천 경로 목록")
public record TransitRouteResponse(
        @Schema(example = "transit") String mode,
        @Schema(example = "tmap") String provider,
        List<Itinerary> itineraries
) implements RouteResult {
    public record Itinerary(long totalTimeSeconds, long totalDistanceMeters, int transferCount,
            long totalWalkDistanceMeters, long totalWalkTimeSeconds, long totalFare, List<Leg> legs) { }

    public record Leg(String mode, long distanceMeters, long durationSeconds, String routeName,
            String routeId, String routeColor, Integer routeType, Stop start, Stop end,
            List<RouteCoordinate> path, List<Stop> stations) { }

    public record Stop(String name, Double latitude, Double longitude, String stationId) { }
}
