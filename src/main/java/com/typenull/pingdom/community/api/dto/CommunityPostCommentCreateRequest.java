package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "커뮤니티 게시글 댓글 등록 요청")
public record CommunityPostCommentCreateRequest(
        @Schema(description = "댓글 내용", example = "저도 가보고 싶네요!")
        @NotBlank @Size(max = 1000) String content
) {
}
