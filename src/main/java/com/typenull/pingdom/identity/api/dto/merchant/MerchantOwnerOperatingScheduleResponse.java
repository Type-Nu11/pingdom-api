package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Merchant Owner 장소 영업시간 변경 응답")
public record MerchantOwnerOperatingScheduleResponse(
        Long placeId,
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> operatingExceptions,
        String message
) {
}
