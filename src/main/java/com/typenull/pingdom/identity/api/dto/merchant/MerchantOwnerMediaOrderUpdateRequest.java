package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "Merchant Owner 탐색 미디어 순서 변경 요청")
public record MerchantOwnerMediaOrderUpdateRequest(
        @PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
        @Schema(description = "0부터 시작하는 이동 대상 위치입니다. 현재 탐색 미디어 개수보다 작은 값만 허용됩니다.", example = "0")
        int displayOrder
) {
}
