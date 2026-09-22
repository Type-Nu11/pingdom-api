package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOnboardingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 온보딩 상태·0~100 완료율과 선택적인 완료 시각을 전달합니다.
 * COMPLETED에서 시각을 생략하면 서비스가 현재 시각을 사용하고 다른 상태의 완료 시각은 도메인에서 거부합니다.
 */
public record MerchantOnboardingUpdateRequest(
        @NotNull MerchantOnboardingStatus status,
        @NotNull @Min(0) @Max(100) Integer completionRate,
        LocalDateTime completedAt,
        @Size(max = 500) String reason
) {
}
