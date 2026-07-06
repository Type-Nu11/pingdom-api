package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.domain.user.AdminBannedUserSortBy;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

public record AdminBannedUserSearchCondition(
        String keyword,
        UserBanType banType,
        LocalDateTime bannedFrom,
        LocalDateTime bannedTo,
        AdminBannedUserSortBy sortBy,
        Sort.Direction sortDirection
) {
}
