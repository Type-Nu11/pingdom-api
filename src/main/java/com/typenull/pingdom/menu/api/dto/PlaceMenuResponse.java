package com.typenull.pingdom.menu.api.dto;

import com.typenull.pingdom.menu.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record PlaceMenuResponse(Long id, Long placeId, String name,
        @Schema(nullable = true, description = "메뉴 설명이 없으면 null") String description, long priceAmount,
        @Schema(description = "가격 통화. 금액은 해당 통화의 기본 단위로 저장") MenuCurrency currency,
        @Schema(nullable = true, description = "대표 이미지가 없으면 null") String imageUrl,
        PlaceMenuStatus status, int displayOrder,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static PlaceMenuResponse from(PlaceMenu menu) {
        return new PlaceMenuResponse(menu.getId(), menu.getPlaceId(), menu.getName(), menu.getDescription(),
                menu.getPriceAmount(), menu.getCurrency(), menu.getImageUrl(), menu.getStatus(), menu.getDisplayOrder(),
                menu.getCreatedAt(), menu.getUpdatedAt());
    }
}
