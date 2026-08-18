package com.typenull.pingdom.moderation.api.dto.ad;

import com.typenull.pingdom.moderation.domain.ad.AdminAdDisplayStatus;
import java.time.LocalDateTime;

public record AdminAdListItem(Long adId, String title, String imageUrl, String redirectUrl,
        LocalDateTime startAt, LocalDateTime endAt, AdminAdDisplayStatus displayStatus,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}
