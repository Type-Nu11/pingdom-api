package com.typenull.pingdom.verification.api.dto;

import java.util.List;

/** 본인 현장 제보의 1부터 시작하는 페이지와 전체 건수·다음 페이지 여부다. */
public record MyScoutFieldReportPageResponse(
        List<MyScoutFieldReportResponse> reports,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
