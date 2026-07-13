package com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminPlaceRecommendationTrafficUpdateResponse(
        @Schema(description = "기본 추천 버전", example = "place-rec-v1")
        String defaultVersion,

        @Schema(description = "현재 추천 버전별 트래픽 정책")
        List<AdminPlaceRecommendationTrafficPolicyItem> policies,

        @Schema(description = "수정 결과 메시지", example = "추천 버전 트래픽 비율을 수정했습니다.")
        String message
) {
}
