package com.typenull.pingdom.place.domain.place.operating.notice;

public enum PlaceOperatingNoticeStatus {
    SCHEDULED,
    ACTIVE,
    EXPIRED,
    CANCELED;

    public boolean isTerminal() {
        return this == EXPIRED || this == CANCELED;
    }
}
