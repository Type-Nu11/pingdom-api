package com.typenull.pingdom.verification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 Scout 프로필 목록 응답")
public record ScoutProfilePageResponse(
        List<ScoutProfileResponse> profiles,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
}
