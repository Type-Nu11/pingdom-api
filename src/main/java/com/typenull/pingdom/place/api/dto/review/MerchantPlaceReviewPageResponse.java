package com.typenull.pingdom.place.api.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "상점주 관리 장소 리뷰 목록 페이지")
public record MerchantPlaceReviewPageResponse(
        List<MerchantPlaceReviewResponse> reviews,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
