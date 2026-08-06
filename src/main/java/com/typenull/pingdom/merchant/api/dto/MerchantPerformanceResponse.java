package com.typenull.pingdom.merchant.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Merchant 소유 장소의 탐색·예약 전환 성과 요약")
public record MerchantPerformanceResponse(
        @Schema(description = "소유 장소 수", example = "3")
        long placeCount,
        @Schema(description = "추천 카드 노출 수", example = "1200")
        long exposureCount,
        @Schema(description = "추천 카드 클릭 수", example = "240")
        long clickCount,
        @Schema(description = "장소 북마크 수", example = "85")
        long bookmarkCount,
        @Schema(description = "전체 예약 수", example = "42")
        long reservationCount,
        @Schema(description = "확정 예약 수", example = "36")
        long confirmedReservationCount,
        @Schema(description = "노출 대비 클릭률(%)", example = "20.0")
        double clickThroughRate,
        @Schema(description = "클릭 대비 예약 전환율(%)", example = "17.5")
        double reservationConversionRate
) {}
