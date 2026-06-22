package com.typenull.pingdom.notification.outbox;

public record MapImageLikedOutboxPayload(
        Long mapImageId,
        Long ownerId,
        Long likerId
) {
}
