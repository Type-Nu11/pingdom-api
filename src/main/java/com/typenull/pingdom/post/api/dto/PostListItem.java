package com.typenull.pingdom.post.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "게시글 목록 항목")
public record PostListItem(
        @Schema(description = "게시글 ID", example = "10")
        Long id,
        @Schema(description = "게시글 제목", example = "남강 야경")
        String title,
        @Schema(description = "게시글 이미지 URL", example = "https://example.com/images/post-10.jpg")
        String imageUrl,
        @Schema(description = "게시글 설명", example = "남강 산책 중 찍은 사진입니다.")
        String description,
        @Schema(description = "작성자 사용자 ID", example = "3")
        Long userId,
        @Schema(description = "작성자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "작성 시각", example = "2026-06-04T16:20:00")
        LocalDateTime createdAt,
        @Schema(description = "좋아요 수", example = "12")
        long likeCount,
        @Schema(description = "연결된 장소 ID", example = "5")
        Long placeId,
        @Schema(description = "연결된 장소명", example = "진주성")
        String placeName
) {
}
