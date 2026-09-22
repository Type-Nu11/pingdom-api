package com.typenull.pingdom.verification.api.dto;

import java.util.List;

/** 관리자용 방문 제보 정정 목록과 1부터 시작하는 페이지 정보. */
public record VisitorVerificationReportCorrectionPageResponse(
        List<VisitorVerificationReportCorrectionResponse> corrections,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
