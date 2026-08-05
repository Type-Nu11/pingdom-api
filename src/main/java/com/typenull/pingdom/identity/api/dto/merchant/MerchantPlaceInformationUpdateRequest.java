package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Merchant 장소 정보 수정 요청")
public record MerchantPlaceInformationUpdateRequest(
        @Size(max = 1000)
        @Schema(description = "관광객에게 노출할 장소 소개", example = "K-컬처 굿즈와 체험을 함께 즐길 수 있는 공간입니다.", nullable = true)
        String description,

        @Size(max = 30)
        @Schema(description = "장소 문의 전화번호", example = "02-1234-5678", nullable = true)
        String contactPhone,

        @Size(max = 500)
        @Pattern(regexp = "^https?://\\S+$", message = "웹사이트 URL은 http:// 또는 https://로 시작해야 합니다.")
        @Schema(description = "장소 웹사이트 URL", example = "https://example.com/place", nullable = true)
        String websiteUrl,

        @Size(max = 500)
        @Pattern(regexp = "^https?://\\S+$", message = "예약 URL은 http:// 또는 https://로 시작해야 합니다.")
        @Schema(description = "예약 페이지 URL", example = "https://example.com/reservations", nullable = true)
        String reservationUrl
) {
}
