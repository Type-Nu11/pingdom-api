package com.typenull.pingdom.moderation.api.dto.place.quality;

import com.typenull.pingdom.place.api.dto.place.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceRegularOperatingHourResponse;
import java.util.List;

public record AdminMapPlaceOperatingScheduleUpdateResponse(
        Long placeId,
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> exceptions,
        String message
) {
}
