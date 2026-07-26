package com.typenull.pingdom.payment.api.dto;

import java.util.List;

public record PaymentPageResponse(
        List<PaymentResponse> payments,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
