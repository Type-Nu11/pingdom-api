package com.typenull.pingdom.menu.api.dto;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "사용자 표시 통화로 환산한 메뉴 참고 가격")
public record MenuConvertedPriceResponse(
        @Schema(description = "환산 금액. 실제 결제 기준은 메뉴의 원래 금액과 통화입니다.", example = "6.43")
        BigDecimal amount,
        @Schema(description = "환산 통화", example = "USD")
        MenuCurrency currency,
        @Schema(description = "환율 기준 일자. 원래 통화와 표시 통화가 같으면 null입니다.", nullable = true)
        LocalDate rateDate
) {
}
