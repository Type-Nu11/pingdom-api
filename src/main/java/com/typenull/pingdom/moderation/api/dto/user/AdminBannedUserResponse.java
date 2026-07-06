package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "밴 유저 목록 조회 응답")
public record AdminBannedUserResponse(
        @Schema(description = "현재 페이지의 밴 사용자 목록")
        List<AdminBannedUserItem> users,
        @Schema(description = "현재 페이지 번호(1부터 시작)", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int limit,
        @Schema(description = "조회 조건에 맞는 현재 밴 사용자 전체 수", example = "12")
        long totalCount,
        @Schema(description = "전체 페이지 수", example = "1")
        long totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext,
        @Schema(description = "현재 밴 중인 사용자 기준 필터별 카운트. keyword 검색어가 있으면 검색 결과 기준으로 계산됩니다.")
        AdminBannedUserCounts counts
) {
    public static AdminBannedUserResponse of(
            List<AdminBannedUserItem> users,
            int page,
            int limit,
            long totalCount,
            long totalPages,
            AdminBannedUserCounts counts
    ) {
        return new AdminBannedUserResponse(users, page, limit, totalCount, totalPages, page < totalPages, counts);
    }
}
