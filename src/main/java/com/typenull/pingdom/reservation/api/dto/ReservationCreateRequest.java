package com.typenull.pingdom.reservation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReservationCreateRequest(
        @NotNull Long availabilityId,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Min(1) int quantity,
        @NotBlank @Size(max = 100) String bookerName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[0-9+()\\- ]+$") String bookerPhone,
        @Size(max = 500) String requestNote,
        @Size(min = 36, max = 36)
        @io.swagger.v3.oas.annotations.media.Schema(nullable = true, description = "최신 견적의 확인 토큰. 생략하면 기존 터치 예약 계약이며 확인 조건 보장은 적용되지 않음")
        String confirmationToken
) {
    public ReservationCreateRequest(Long availabilityId, String idempotencyKey, int quantity,
            String bookerName, String bookerPhone, String requestNote) {
        this(availabilityId, idempotencyKey, quantity, bookerName, bookerPhone, requestNote, null);
    }
    public ReservationCreateRequest(Long availabilityId, String idempotencyKey, int quantity) {
        this(availabilityId, idempotencyKey, quantity, "예약자", "00000000", null);
    }
}
