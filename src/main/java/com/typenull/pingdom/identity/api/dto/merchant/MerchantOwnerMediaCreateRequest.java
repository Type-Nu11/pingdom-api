package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "업로드를 완료한 Merchant Owner 탐색 미디어 등록 요청")
public record MerchantOwnerMediaCreateRequest(
        @NotBlank(message = "s3Key는 필수입니다.")
        @Size(max = 500, message = "s3Key는 500자 이하여야 합니다.")
        @Schema(description = "업로드 URL 발급 응답의 s3Key", requiredMode = Schema.RequiredMode.REQUIRED)
        String s3Key,

        @PositiveOrZero(message = "노출 순서는 0 이상이어야 합니다.")
        @Schema(nullable = true, description = "미입력 시 현재 탐색 미디어 마지막 순서 다음으로 등록됩니다.")
        Integer displayOrder
) {
}
