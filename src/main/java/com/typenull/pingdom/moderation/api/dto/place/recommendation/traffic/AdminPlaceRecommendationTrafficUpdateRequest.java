package com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminPlaceRecommendationTrafficUpdateRequest(
        @NotBlank(message = "reason은 필수입니다.")
        @Size(max = 500, message = "reason은 500자 이하여야 합니다.")
        @Schema(description = "추천 정책 변경 사유", example = "추천 실험 v2 트래픽을 확대합니다.")
        String reason,

        @Valid
        @NotEmpty(message = "policies는 1개 이상이어야 합니다.")
        @Schema(description = "수정할 추천 버전별 트래픽 비율 목록")
        List<AdminPlaceRecommendationTrafficUpdateItem> policies
) {
}
