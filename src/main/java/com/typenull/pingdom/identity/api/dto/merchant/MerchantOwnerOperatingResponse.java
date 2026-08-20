package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Merchant Owner 장소 운영 정보 응답")
public record MerchantOwnerOperatingResponse(
        Long placeId,
        PlaceOperatingStatus operatingStatus,
        LocalDateTime operatingStatusCheckedAt,
        boolean currentlyOperating,
        LocalDateTime checkedAt,
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> operatingExceptions,
        PlaceInformationSourceType informationSource,
        PlaceInformationVerificationStatus verificationStatus
) {
}
