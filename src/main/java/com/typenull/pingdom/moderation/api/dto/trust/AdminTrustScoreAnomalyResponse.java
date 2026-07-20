package com.typenull.pingdom.moderation.api.dto.trust;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminTrustScoreAnomalyResponse(
        @Schema(description = "이상치 목록")
        List<AdminTrustScoreAnomalyItem> anomalies,
        @Schema(description = "현재 페이지", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int limit,
        @Schema(description = "전체 개수", example = "42")
        long totalCount,
        @Schema(description = "전체 페이지 수", example = "3")
        int totalPages
) {
}
