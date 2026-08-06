package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.admin.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 역할 할당 요청")
public record AdminRoleAssignmentRequest(
        @NotNull
        @Schema(description = "할당할 관리자 역할", example = "CONTENT_MODERATOR")
        AdminRole role,

        @Size(max = 500)
        @Schema(description = "역할을 부여하거나 회수하는 사유", example = "장소 정보 검수 담당 배정")
        String reason
) {
}
