package com.typenull.pingdom.identity.event;

import com.typenull.pingdom.identity.domain.merchant.MerchantOnboardingStatus;
import java.time.LocalDateTime;

public record MerchantOnboardingUpdatedEvent(
        Long merchantOwnerUserId,
        MerchantOnboardingStatus beforeStatus,
        MerchantOnboardingStatus afterStatus,
        Integer beforeCompletionRate,
        Integer afterCompletionRate,
        LocalDateTime occurredAt
) {
}
