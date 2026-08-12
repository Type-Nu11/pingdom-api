package com.typenull.pingdom.moderation.api.dto.place.query;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminMapPlaceImageItem(
        Long id,
        String imageUrl,
        String thumbnailUrl,
        String title,
        String description,
        Long userId,
        String username,
        LocalDateTime createdAt,
        long likeCount,
        @Schema(
                description = "일반 사용자 기준 게시글 노출 상태",
                example = "VISIBLE"
        )
        AdminMapPlacePostVisibilityStatus visibilityStatus,
        @Schema(
                description = "게시글이 숨김 처리된 사유. 노출 중인 게시글은 null",
                nullable = true,
                example = "ADMIN_HIDDEN"
        )
        String hiddenReason
) {
}
