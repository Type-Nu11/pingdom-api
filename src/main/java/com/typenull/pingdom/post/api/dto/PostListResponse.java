package com.typenull.pingdom.post.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "게시글 목록 조회 응답")
public record PostListResponse(
        @Schema(description = "게시글 목록")
        List<PostListItem> posts,
        @Schema(description = "현재 페이지", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int limit,
        @Schema(description = "전체 게시글 수", example = "125")
        long totalCount,
        @Schema(description = "전체 페이지 수", example = "7")
        long totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext
) {
    public static PostListResponse of(List<PostListItem> posts, int page, int limit, long totalCount, long totalPages) {
        return new PostListResponse(posts, page, limit, totalCount, totalPages, page < totalPages);
    }
}
