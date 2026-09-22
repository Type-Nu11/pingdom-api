package com.typenull.pingdom.verification.api.dto;

import java.util.List;

/** 관리자용 현장 제보 목록과 1부터 시작하는 페이지 정보. */
public record ScoutFieldReportPageResponse(
        List<ScoutFieldReportResponse> reports,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
