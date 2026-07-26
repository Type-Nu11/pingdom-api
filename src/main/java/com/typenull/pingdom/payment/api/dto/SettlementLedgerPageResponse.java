package com.typenull.pingdom.payment.api.dto;

import java.util.List;

public record SettlementLedgerPageResponse(
        List<SettlementLedgerResponse> entries,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
