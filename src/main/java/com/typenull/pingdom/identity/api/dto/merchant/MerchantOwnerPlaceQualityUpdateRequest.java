package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 장소 운영 품질 상태와 0~100 정수 백분율 지표를 갱신.
 * 평가 시각을 생략하면 관리 서비스가 현재 시각을 사용.
 */
public record MerchantOwnerPlaceQualityUpdateRequest(
        @NotNull MerchantOperationalQualityStatus status,
        @NotNull @Min(0) @Max(100) Integer reservationResponseRate,
        @NotNull @Min(0) @Max(100) Integer reservationCancellationRate,
        @NotNull @Min(0) @Max(100) Integer noShowRate,
        LocalDateTime evaluatedAt,
        @Size(max = 500) String reason
) {
}
