package com.typenull.pingdom.payment.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관광객 결제 목록 페이지 응답")
public record PaymentPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PaymentResponse> payments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasNext
) {}
