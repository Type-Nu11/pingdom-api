package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "커뮤니티 게시글 댓글 등록 요청")
public record CommunityPostCommentCreateRequest(
        @Schema(description = "댓글 내용", example = "저도 가보고 싶네요!")
        @NotBlank @Size(max = 1000) String content,
        @Schema(nullable = true, description = "같은 게시글의 최상위 댓글 ID. 생략하면 최상위 댓글 작성")
        @Positive Long parentCommentId
) {
}
