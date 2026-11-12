package com.typenull.pingdom.analysis.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "보관된 입지 분석 보고서 요약 페이지")
public record LocationAnalysisReportPageResponse(
        @Schema(description = "본문을 제외한 보고서 요약 목록")
        List<LocationAnalysisReportResponse> reports,
        @Schema(description = "1부터 시작하는 현재 페이지", example = "1")
        int page,
        @Schema(description = "페이지 크기. 최대 100", example = "20")
        int limit,
        @Schema(description = "소유한 보고서의 전체 개수")
        long totalCount,
        @Schema(description = "전체 페이지 수")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부")
        boolean hasNext
) {
}
