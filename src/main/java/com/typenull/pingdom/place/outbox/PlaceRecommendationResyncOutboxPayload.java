package com.typenull.pingdom.place.outbox;

public record PlaceRecommendationResyncOutboxPayload(
        Long placeId,
        String reason
) {
}
