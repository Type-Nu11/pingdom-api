package com.typenull.pingdom.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "예약 상품 등록 요청")
public record ReservableProductCreateRequest(
        @NotNull @Positive
        @Schema(description = "상품을 등록할 장소 ID", example = "101", requiredMode = Schema.RequiredMode.REQUIRED)
        Long placeId,
        @NotNull
        @Schema(description = "예약 상품 유형. 장소 자체 예약인 GENERAL은 등록할 수 없습니다.",
                example = "TICKET", requiredMode = Schema.RequiredMode.REQUIRED)
        ReservableProductType productType,
        @NotBlank @Size(max = 100)
        @Schema(description = "예약 상품명", example = "이월드 오후 입장권",
                maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String name
) {}
