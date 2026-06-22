package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Locale;
import org.springframework.util.StringUtils;

public enum PlaceSearchSort {
    LATEST,
    NEAREST;

    public static PlaceSearchSort from(String value) {
        if (!StringUtils.hasText(value)) {
            return LATEST;
        }

        try {
            return PlaceSearchSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.UNSUPPORTED_PLACE_SEARCH_SORT);
        }
    }
}
