package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewRecommendReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 리뷰 본문과 추천 사유, 미리 업로드한 사진 ID를 검증합니다.
 * 추천 사유는 기존 단일 문자열과 새 코드 목록 중 하나만 허용하며 외부 imageUrls 입력과 중복 사진 ID는 거부합니다.
 */
@Schema(description = "장소 리뷰 작성 요청. recommendReasons와 reviewMediaIds가 새 표준 계약입니다.")
public record PlaceReviewCreateRequest(
        @Deprecated
        @Size(max = 100)
        @Schema(description = "기존 단일 추천 이유. recommendReasons가 없을 때만 사용할 수 있습니다.", deprecated = true)
        String recommendReason,

        @Size(min = 1, max = 5)
        @Schema(description = "추천 이유 코드. 1~5개를 입력하며 저장·응답 순서가 유지됩니다.")
        List<@NotNull PlaceReviewRecommendReason> recommendReasons,

        @NotBlank @Size(max = 2000)
        String content,

        @Size(max = 3)
        @Schema(description = "리뷰 사진 업로드 API가 반환한 ID. 최대 3개이며 입력 순서가 사진 표시 순서입니다.")
        List<@Positive Long> reviewMediaIds,

        @Deprecated
        @Size(max = 3)
        @Schema(description = "임의 URL 입력은 지원하지 않습니다. 빈 배열만 허용하며 reviewMediaIds를 사용해야 합니다.", deprecated = true)
        List<@Size(max = 500) String> imageUrls
) {
    @AssertTrue(message = "recommendReasons 또는 기존 recommendReason 중 하나만 입력해야 합니다.")
    @Schema(hidden = true)
    public boolean hasValidRecommendReasonContract() {
        boolean hasLegacyReason = StringUtils.hasText(recommendReason);
        boolean hasNewReasons = recommendReasons != null && !recommendReasons.isEmpty();
        return hasLegacyReason != hasNewReasons
                && (recommendReasons == null || recommendReasons.size() == new HashSet<>(recommendReasons).size());
    }

    @AssertTrue(message = "imageUrls는 비워두고 reviewMediaIds를 사용해야 합니다.")
    @Schema(hidden = true)
    public boolean hasNoExternalImageUrls() {
        return imageUrls == null || imageUrls.isEmpty();
    }

    @AssertTrue(message = "reviewMediaIds에는 중복된 사진을 포함할 수 없습니다.")
    @Schema(hidden = true)
    public boolean hasDistinctReviewMediaIds() {
        return reviewMediaIds == null || reviewMediaIds.size() == new HashSet<>(reviewMediaIds).size();
    }
}
