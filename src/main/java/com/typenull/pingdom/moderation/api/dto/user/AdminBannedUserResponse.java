package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "밴 유저 목록 조회 응답")
public record AdminBannedUserResponse(
        List<AdminBannedUserItem> users,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
    public static AdminBannedUserResponse of(
            List<AdminBannedUserItem> users,
            int page,
            int limit,
            long totalCount,
            long totalPages
    ) {
        return new AdminBannedUserResponse(users, page, limit, totalCount, totalPages, page < totalPages);
    }
}
