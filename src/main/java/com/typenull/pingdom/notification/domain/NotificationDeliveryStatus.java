package com.typenull.pingdom.notification.domain;

public enum NotificationDeliveryStatus {
    SUCCEEDED,
    FAILED,
    RETRY_SCHEDULED,
    FINAL_FAILED
}
