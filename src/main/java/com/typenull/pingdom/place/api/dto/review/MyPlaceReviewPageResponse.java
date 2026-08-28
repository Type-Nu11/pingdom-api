package com.typenull.pingdom.place.api.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내가 작성한 장소 리뷰 목록 페이지")
public record MyPlaceReviewPageResponse(
        List<MyPlaceReviewResponse> reviews,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
