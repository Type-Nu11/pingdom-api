package com.typenull.pingdom.place.domain.place.core;

public enum MapBookmarkTrendEventType {
    BASELINE_ACTIVE,
    ADDED,
    REMOVED;

    public boolean isBookmarked() {
        return this != REMOVED;
    }
}
