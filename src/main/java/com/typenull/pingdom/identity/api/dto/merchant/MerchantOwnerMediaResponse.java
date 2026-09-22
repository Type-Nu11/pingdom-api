package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 탐색 이미지 목록과 현재 대표 이미지의 미디어 ID입니다.
 * 장소 대표 URL과 일치하는 탐색 이미지가 없으면 representativeMediaId는 null입니다.
 */
@Schema(description = "Merchant Owner 탐색 미디어 목록 응답")
public record MerchantOwnerMediaResponse(
        Long placeId,
        Long representativeMediaId,
        List<PlaceMediaItem> media
) {
}
