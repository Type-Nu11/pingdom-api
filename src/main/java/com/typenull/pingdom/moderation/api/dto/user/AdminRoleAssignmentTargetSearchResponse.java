package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 역할 부여 대상 사용자 검색 응답")
public record AdminRoleAssignmentTargetSearchResponse(
        @Schema(description = "현재 페이지의 역할 부여 대상 사용자")
        List<AdminRoleAssignmentTargetItem> users,
        @Schema(description = "현재 페이지 번호(1부터 시작)", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int limit,
        @Schema(description = "검색 조건에 맞는 전체 사용자 수", example = "12")
        long totalCount,
        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext
) {

    public static AdminRoleAssignmentTargetSearchResponse of(
            List<AdminRoleAssignmentTargetItem> users,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminRoleAssignmentTargetSearchResponse(
                users,
                page,
                limit,
                totalCount,
                totalPages,
                page < totalPages
        );
    }
}
