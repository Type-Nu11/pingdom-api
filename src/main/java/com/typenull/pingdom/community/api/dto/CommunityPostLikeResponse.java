package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "커뮤니티 게시글 좋아요 상태 응답")
public record CommunityPostLikeResponse(
        @Schema(description = "게시글 ID", example = "1") long postId,
        @Schema(description = "좋아요 수", example = "12") long likeCount,
        @Schema(description = "현재 사용자의 좋아요 여부", example = "true") boolean liked
) {
}
