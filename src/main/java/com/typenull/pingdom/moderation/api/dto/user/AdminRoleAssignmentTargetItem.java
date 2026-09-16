package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 역할 부여 대상 사용자")
public record AdminRoleAssignmentTargetItem(
        @Schema(description = "역할 API에 전달할 사용자 ID", example = "7")
        Long userId,
        @Schema(description = "사용자명", example = "admin_operator")
        String username
) {

    public static AdminRoleAssignmentTargetItem from(User user) {
        return new AdminRoleAssignmentTargetItem(user.getId(), user.getUsername());
    }
}
