package com.typenull.pingdom.place.api.dto.trend;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Locale;

public enum PlaceTrendPeriod {
    WEEK;

    public static PlaceTrendPeriod from(String value) {
        try {
            return value == null ? WEEK : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new MapException(MapErrorCode.UNSUPPORTED_TREND_PERIOD);
        }
    }
}
