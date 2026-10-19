package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

@Schema(description = "본인 리뷰 부분 수정. 누락·null 필드는 유지합니다. 사진 ID 배열은 유지할 기존 사진과 신규 업로드의 최종 순서이며 빈 배열은 모든 사진을 제거합니다.")
public record PlaceReviewUpdateRequest(
        @Deprecated @Size(max = 100)
        @Schema(description = "기존 단일 추천 이유. recommendReasons와 함께 입력할 수 없습니다.", deprecated = true)
        String recommendReason,
        @Size(min = 1, max = 5)
        @Schema(description = "추천 이유 코드 1~5개. 중복 불가, 요청 순서 유지. 첫 코드가 recommendReason에도 반영됩니다.")
        List<@NotNull PlaceReviewRecommendReason> recommendReasons,
        @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*", message = "content는 공백일 수 없습니다.")
        String content,
        @Size(max = 3)
        @Schema(description = "최종 사진 ID 순서. 본인의 동일 리뷰 연결 사진과 동일 장소의 미연결·미만료 업로드만 허용합니다. null은 기존 사진 유지, []는 전체 제거입니다.")
        List<@NotNull @Positive Long> reviewMediaIds,
        @Deprecated @Size(max = 0)
        @Schema(description = "임의 URL은 입력할 수 없습니다. 생략하거나 빈 배열을 사용합니다.", deprecated = true)
        List<String> imageUrls
) {
    @AssertTrue(message = "추천 이유는 공백이 아닌 단일 값 또는 중복 없는 코드 목록 중 하나만 입력해야 합니다.")
    @Schema(hidden = true)
    public boolean isValidRecommendReasonContract() {
        if (recommendReason != null) {
            return !recommendReason.isBlank() && recommendReasons == null;
        }
        return recommendReasons == null || !recommendReasons.isEmpty()
                && recommendReasons.size() == new HashSet<>(recommendReasons).size();
    }

    @AssertTrue(message = "reviewMediaIds에는 중복된 사진을 포함할 수 없습니다.")
    @Schema(hidden = true)
    public boolean isDistinctReviewMediaIds() {
        return reviewMediaIds == null || reviewMediaIds.size() == new HashSet<>(reviewMediaIds).size();
    }
}
