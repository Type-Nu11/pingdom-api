package com.typenull.pingdom.place.domain.place.information.report;

public enum PlaceInformationDisputeStatus {
    SUBMITTED,
    ACCEPTED,
    REJECTED;

    public boolean isProcessed() {
        return this == ACCEPTED || this == REJECTED;
    }
}
