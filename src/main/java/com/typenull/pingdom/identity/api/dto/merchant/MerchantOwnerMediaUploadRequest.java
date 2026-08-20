package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Merchant Owner 탐색 미디어 업로드 URL 요청")
public record MerchantOwnerMediaUploadRequest(
        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,
        @NotBlank(message = "MIME 타입은 필수입니다.")
        String contentType,
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 양수여야 합니다.")
        @Max(value = 10_485_760, message = "파일은 10MB 이하여야 합니다.")
        Long fileSize
) {
}
