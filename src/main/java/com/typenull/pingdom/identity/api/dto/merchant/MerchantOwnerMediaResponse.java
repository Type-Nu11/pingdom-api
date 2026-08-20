package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.api.dto.place.media.PlaceMediaItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Merchant Owner 탐색 미디어 목록 응답")
public record MerchantOwnerMediaResponse(
        Long placeId,
        Long representativeMediaId,
        List<PlaceMediaItem> media
) {
}
