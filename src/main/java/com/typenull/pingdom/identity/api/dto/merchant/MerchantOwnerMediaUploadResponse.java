package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Merchant Owner 탐색 미디어 업로드 URL 응답")
public record MerchantOwnerMediaUploadResponse(
        String uploadUrl,
        String imageUrl,
        String s3Key,
        LocalDateTime expiresAt
) {
}
