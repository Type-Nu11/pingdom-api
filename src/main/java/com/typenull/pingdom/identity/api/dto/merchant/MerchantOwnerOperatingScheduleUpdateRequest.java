package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingExceptionRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceRegularOperatingHourRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Set;

@Schema(description = "Merchant Owner 장소 영업시간 변경 요청")
public record MerchantOwnerOperatingScheduleUpdateRequest(
        Set<@Valid AdminMapPlaceRegularOperatingHourRequest> regularHours,
        Set<@Valid AdminMapPlaceOperatingExceptionRequest> exceptions
) {
}
