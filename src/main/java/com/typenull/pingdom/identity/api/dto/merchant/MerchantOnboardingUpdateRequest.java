package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOnboardingStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record MerchantOnboardingUpdateRequest(
        @NotNull MerchantOnboardingStatus status,
        @NotNull @Min(0) @Max(100) Integer completionRate,
        LocalDateTime completedAt,
        @Size(max = 500) String reason
) {
}
