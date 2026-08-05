package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 역할 회수 요청")
public record AdminRoleAssignmentRevokeRequest(
        @Size(max = 500)
        @Schema(description = "역할을 회수하는 사유", example = "담당 업무 변경")
        String reason
) {
}
