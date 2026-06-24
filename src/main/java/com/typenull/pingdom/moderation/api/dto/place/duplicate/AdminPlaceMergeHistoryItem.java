package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import java.time.LocalDateTime;

public record AdminPlaceMergeHistoryItem(
        Long historyId,
        Long sourcePlaceId,
        Long targetPlaceId,
        Long adminUserId,
        boolean restored,
        LocalDateTime mergedAt,
        LocalDateTime restoredAt
) {
}
