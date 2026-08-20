package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Merchant Owner 대표 미디어 지정 요청")
public record MerchantOwnerRepresentativeMediaUpdateRequest(
        @NotNull(message = "미디어 ID는 필수입니다.")
        Long mediaId
) {
}
