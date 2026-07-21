package com.typenull.pingdom.place.api.dto.place.information.report;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장소 정보 신고 페이지 응답")
public record PlaceInformationReportPageResponse(
        List<PlaceInformationReportResponse> reports,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
}
