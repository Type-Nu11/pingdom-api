package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 밴 사용자 필터별 카운트")
public record AdminBannedUserCounts(
        @Schema(description = "현재 밴 중인 전체 사용자 수", example = "12")
        long total,
        @Schema(description = "현재 영구 밴 중인 사용자 수", example = "7")
        long permanent,
        @Schema(description = "현재 기간 밴 중인 사용자 수", example = "5")
        long temporary
) {
}
