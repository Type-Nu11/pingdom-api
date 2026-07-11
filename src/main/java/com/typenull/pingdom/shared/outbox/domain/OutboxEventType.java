package com.typenull.pingdom.shared.outbox.domain;

public enum OutboxEventType {
    EMAIL_VERIFICATION_REQUESTED,
    PASSWORD_RESET_REQUESTED,
    MAP_IMAGE_LIKED,
    PLACE_RECOMMENDATION_RESYNC_REQUESTED,
    S3_OBJECT_DELETE_REQUESTED
}
