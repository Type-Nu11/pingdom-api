package com.typenull.pingdom.moderation.api.dto.place.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AdminPlaceRecommendationTrafficUpdateRequest(
        @Valid
        @NotEmpty(message = "policies는 1개 이상이어야 합니다.")
        @Schema(description = "수정할 추천 버전별 트래픽 비율 목록")
        List<AdminPlaceRecommendationTrafficUpdateItem> policies
) {
}
