package com.typenull.pingdom.place.application.service.place.operating;

import java.time.LocalDateTime;

public record PlaceCurrentOperatingState(
        boolean currentlyOperating,
        LocalDateTime checkedAt
) {
}
