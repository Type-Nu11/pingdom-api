package com.typenull.pingdom.moderation.api.dto.post;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 게시글 검수 상태별 카운트")
public record AdminPostReviewCounts(
        long all,
        long pending,
        long processed,
        long normal
) {
}
