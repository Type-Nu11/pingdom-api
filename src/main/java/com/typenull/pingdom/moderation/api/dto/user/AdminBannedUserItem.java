package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "밴 유저 목록 아이템")
public record AdminBannedUserItem(
        @Schema(description = "사용자 ID", example = "7")
        Long userId,
        @Schema(description = "사용자명", example = "blockedUser01")
        String username,
        @Schema(description = "밴 처리 여부", example = "true")
        boolean banned
) {
}
