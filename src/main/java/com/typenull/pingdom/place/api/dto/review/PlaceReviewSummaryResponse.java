package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장소 전체 공개 리뷰 집계. 리뷰 건수와 추천 태그를 선택한 고유 작성자 수를 구분합니다.")
public record PlaceReviewSummaryResponse(
        @Schema(description = "장소 ID", example = "17", requiredMode = Schema.RequiredMode.REQUIRED)
        long placeId,
        @Schema(description = "VISIBLE 리뷰 전체 건수. 유효 태그나 작성자가 없는 과거 리뷰도 포함합니다.",
                minimum = "0", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalReviewCount,
        @Schema(description = "유효 추천 태그를 하나 이상 선택한 고유 작성자 수. 같은 작성자의 여러 공개 리뷰는 합쳐 계산합니다.",
                minimum = "0", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        long participantCount,
        @Schema(description = "7개 지원 코드를 enum 선언 순서로 모두 반환하며 0건 태그도 포함합니다. 태그별 수의 합은 참여 인원보다 클 수 있습니다.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ReasonCount> reasonCounts
) {
    public PlaceReviewSummaryResponse {
        reasonCounts = List.copyOf(reasonCounts);
    }

    public record ReasonCount(
            @Schema(description = "추천 태그 코드", example = "FRIENDLY", requiredMode = Schema.RequiredMode.REQUIRED)
            PlaceReviewRecommendReason reason,
            @Schema(description = "이 태그를 선택한 고유 작성자 수. 같은 작성자의 반복 태그와 여러 리뷰는 중복 계산하지 않습니다.",
                    minimum = "0", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            long count
    ) {
    }
}
