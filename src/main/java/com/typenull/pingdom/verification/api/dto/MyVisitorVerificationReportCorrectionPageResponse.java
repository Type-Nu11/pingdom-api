package com.typenull.pingdom.verification.api.dto;

import java.util.List;

public record MyVisitorVerificationReportCorrectionPageResponse(
        List<MyVisitorVerificationReportCorrectionResponse> corrections,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
