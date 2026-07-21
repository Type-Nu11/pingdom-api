package com.typenull.pingdom.place.domain.place.information.report;

public enum PlaceInformationReportStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ACCEPTED,
    REJECTED,
    DISPUTED,
    RESOLVED,
    CANCELED;

    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == RESOLVED || this == CANCELED;
    }
}
