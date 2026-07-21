package com.typenull.pingdom.verification.api.dto;

import java.util.List;

public record VisitorVerificationReportCorrectionPageResponse(
        List<VisitorVerificationReportCorrectionResponse> corrections,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
