package com.typenull.pingdom.engagement.api.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "내 신고 내역 조회 응답")
public record MyPostReportResponse(
        @Schema(description = "내 신고 내역 목록")
        List<MyPostReportItem> reports,
        @Schema(description = "현재 페이지", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int limit,
        @Schema(description = "전체 신고 수", example = "12")
        long totalCount,
        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext
) {
    public static MyPostReportResponse of(
            List<MyPostReportItem> reports,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new MyPostReportResponse(reports, page, limit, totalCount, totalPages, page < totalPages);
    }
}
