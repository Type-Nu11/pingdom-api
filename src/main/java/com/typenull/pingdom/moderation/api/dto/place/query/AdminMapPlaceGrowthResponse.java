package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.place.domain.place.statistics.PlaceGrowthSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장소 상세 성장 상태")
public record AdminMapPlaceGrowthResponse(
        @Schema(description = "장소 성장에 반영된 노출 게시글 사진 수", example = "10")
        long photoCount,
        @Schema(description = "장소 성장에 반영되지 않는 숨김 게시글 사진 수", example = "1")
        long hiddenPhotoCount,
        int level,
        long currentLevelMinPhotoCount,
        long nextLevelMinPhotoCount,
        int progressPercent
) {

    public static AdminMapPlaceGrowthResponse of(
            PlaceGrowthSnapshot placeGrowth,
            long hiddenPhotoCount
    ) {
        return new AdminMapPlaceGrowthResponse(
                placeGrowth.photoCount(),
                hiddenPhotoCount,
                placeGrowth.level(),
                placeGrowth.currentLevelMinPhotoCount(),
                placeGrowth.nextLevelMinPhotoCount(),
                placeGrowth.progressPercent()
        );
    }
}
