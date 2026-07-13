package com.typenull.pingdom.moderation.api.dto.place.quality.operating;

import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import java.util.List;

public record AdminMapPlaceOperatingScheduleUpdateResponse(
        Long placeId,
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> exceptions,
        String message
) {
}
