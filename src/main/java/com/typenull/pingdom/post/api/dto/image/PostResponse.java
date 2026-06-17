package com.typenull.pingdom.post.api.dto.image;

import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "게시글 처리 응답")
public record PostResponse(
        @Schema(description = "대상 게시글 ID", example = "10")
        Long id,
        @Schema(description = "처리 결과 메시지", example = "게시글을 저장했습니다.")
        String message,
        @Schema(description = "연결된 장소 성장 상태")
        PlaceGrowthSnapshot placeGrowth
) {
}
