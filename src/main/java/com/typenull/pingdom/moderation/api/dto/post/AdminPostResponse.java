package com.typenull.pingdom.moderation.api.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "관리자 게시글 조회 응답")
public record AdminPostResponse(
        List<AdminPostItem> posts,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext,
        AdminPostReviewCounts counts
) {
    public static AdminPostResponse of(
            List<AdminPostItem> posts,
            int page,
            int limit,
            long totalCount,
            long totalPages,
            AdminPostReviewCounts counts
    ) {
        return new AdminPostResponse(posts, page, limit, totalCount, totalPages, page < totalPages, counts);
    }
}
