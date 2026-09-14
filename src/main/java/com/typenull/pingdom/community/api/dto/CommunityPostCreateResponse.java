package com.typenull.pingdom.community.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "커뮤니티 게시글 등록 응답")
public record CommunityPostCreateResponse(
        @Schema(description = "등록된 게시글 ID", example = "1")
        Long postId,
        @Schema(description = "연결된 장소 ID 목록", example = "[1, 2]")
        List<Long> placeIds
) {
}
