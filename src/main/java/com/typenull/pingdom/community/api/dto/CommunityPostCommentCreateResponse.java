package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "커뮤니티 게시글 댓글 등록 응답")
public record CommunityPostCommentCreateResponse(
        @Schema(description = "댓글 ID", example = "1") Long commentId,
        @Schema(description = "대상 게시글 ID", example = "1") Long postId,
        @Schema(description = "저장된 댓글 내용", example = "저도 가보고 싶네요!") String content
) {
}
