package com.typenull.pingdom.moderation.api.dto.place.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 추천 성과 항목")
public record AdminPlaceRecommendationMetricItem(
        @Schema(description = "장소 ID", example = "1")
        Long id,
        @Schema(description = "장소명", example = "진주성")
        String name,
        @Schema(description = "장소 주소", example = "경상남도 진주시 남강로 626")
        String address,
        @Schema(description = "장소 사진 수", example = "12")
        long photoCount,
        @Schema(description = "추천 노출 수", example = "120")
        long exposureCount,
        @Schema(description = "추천 클릭 수", example = "18")
        long clickCount,
        @Schema(description = "원본 CTR", example = "0.15")
        double rawCtr,
        @Schema(description = "랭킹 기준 smoothed CTR", example = "0.13")
        double smoothedCtr,
        @Schema(description = "북마크 전환 수", example = "4")
        long bookmarkConversionCount,
        @Schema(description = "좋아요 전환 수", example = "6")
        long likeConversionCount,
        @Schema(description = "북마크 전환율", example = "0.03")
        double bookmarkConversionRate,
        @Schema(description = "좋아요 전환율", example = "0.05")
        double likeConversionRate,
        @Schema(description = "전체 전환율", example = "0.08")
        double totalConversionRate,
        @Schema(description = "추천 snapshot 최종 갱신 시각", example = "2026-06-05T22:50:43")
        LocalDateTime snapshotUpdatedAt
) {
}
