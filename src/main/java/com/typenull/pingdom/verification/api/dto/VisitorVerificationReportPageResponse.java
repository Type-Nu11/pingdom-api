package com.typenull.pingdom.verification.api.dto;

import java.util.List;

/** 관리자 방문 제보 목록과 1부터 시작하는 페이지·전체 건수 정보. */
public record VisitorVerificationReportPageResponse(
        List<VisitorVerificationReportResponse> reports,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
