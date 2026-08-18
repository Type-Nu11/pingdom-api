package com.typenull.pingdom.place.domain.registration;

public enum PlaceRegistrationTag {
    ENGLISH_SERVICE_AVAILABLE,
    ENGLISH_MENU_AVAILABLE,
    RESERVATION_AVAILABLE,
    RESERVATION_COUPON_AVAILABLE,
    GENERAL_COUPON_AVAILABLE,
    GOOD_AMBIENCE;

    public boolean isDynamic() {
        return this == RESERVATION_AVAILABLE
                || this == RESERVATION_COUPON_AVAILABLE
                || this == GENERAL_COUPON_AVAILABLE;
    }
}
