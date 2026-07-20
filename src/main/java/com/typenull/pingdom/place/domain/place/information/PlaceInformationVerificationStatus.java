package com.typenull.pingdom.place.domain.place.information;

public enum PlaceInformationVerificationStatus {
    UNVERIFIED,
    SOURCE_CONFIRMED,
    OWNER_SUBMITTED,
    ADMIN_VERIFIED,
    REJECTED,
    DISPUTED,
    EXPIRED;

    public boolean isVerified() {
        return this == SOURCE_CONFIRMED || this == ADMIN_VERIFIED;
    }
}
