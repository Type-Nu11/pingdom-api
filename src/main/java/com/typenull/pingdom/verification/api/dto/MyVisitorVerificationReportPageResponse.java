package com.typenull.pingdom.verification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 방문자 검증 제보 목록 페이지 응답")
public record MyVisitorVerificationReportPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MyVisitorVerificationReportResponse> reports,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasNext
) {}
