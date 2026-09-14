package com.typenull.pingdom.menu.api.dto;

import com.typenull.pingdom.menu.domain.PlaceMenu;
import com.typenull.pingdom.menu.domain.PlaceMenuStatus;
import com.typenull.pingdom.menu.domain.MenuCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관광객용 장소 메뉴 응답")
public record PlaceMenuPublicResponse(
        Long id,
        Long placeId,
        String name,
        @Schema(nullable = true, description = "메뉴 설명이 없으면 null") String description,
        @Schema(description = "원래 메뉴 가격. 환산 가격과 무관하게 항상 유지됩니다.", example = "9000")
        long priceAmount,
        @Schema(description = "원래 메뉴 가격의 통화. 실제 결제 기준 통화입니다.", example = "KRW") MenuCurrency currency,
        @Schema(nullable = true, description = "대표 이미지가 없으면 null") String imageUrl,
        PlaceMenuStatus status,
        int displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @Schema(nullable = true, description = "사용자 표시 통화의 참고 환산 가격. 원래 통화와 표시 통화가 다를 때만 반환됩니다.")
        MenuConvertedPriceResponse convertedPrice
) {
    public static PlaceMenuPublicResponse from(PlaceMenu menu, MenuConvertedPriceResponse convertedPrice) {
        return new PlaceMenuPublicResponse(menu.getId(), menu.getPlaceId(), menu.getName(), menu.getDescription(),
                menu.getPriceAmount(), menu.getCurrency(), menu.getImageUrl(), menu.getStatus(), menu.getDisplayOrder(),
                menu.getCreatedAt(), menu.getUpdatedAt(), convertedPrice);
    }
}
