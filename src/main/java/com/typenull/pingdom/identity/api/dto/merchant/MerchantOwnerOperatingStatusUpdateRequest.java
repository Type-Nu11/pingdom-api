package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Merchant Owner 장소 운영 상태 변경 요청")
public record MerchantOwnerOperatingStatusUpdateRequest(
        @NotNull(message = "운영 상태는 필수입니다.")
        @Schema(example = "OPERATING")
        PlaceOperatingStatus operatingStatus
) {
}
