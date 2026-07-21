package com.typenull.pingdom.verification.api.dto;

import java.util.List;

public record MyScoutFieldReportPageResponse(
        List<MyScoutFieldReportResponse> reports,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
