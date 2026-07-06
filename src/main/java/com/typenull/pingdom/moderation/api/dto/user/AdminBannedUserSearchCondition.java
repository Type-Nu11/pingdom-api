package com.typenull.pingdom.moderation.api.dto.user;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.domain.user.AdminBannedUserSortBy;
import java.time.LocalDateTime;
import org.springframework.data.domain.Sort;

public record AdminBannedUserSearchCondition(
        String keyword,
        UserBanType banType,
        LocalDateTime bannedFrom,
        LocalDateTime bannedTo,
        AdminBannedUserSortBy sortBy,
        Sort.Direction sortDirection
) {

    public String normalizedKeyword() {
        if (keyword == null) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : trimmedKeyword;
    }

    public boolean isNumericKeyword() {
        String normalizedKeyword = normalizedKeyword();
        return normalizedKeyword != null && normalizedKeyword.chars().allMatch(Character::isDigit);
    }

    public AdminBannedUserSortBy normalizedSortBy() {
        return sortBy == null ? AdminBannedUserSortBy.BANNED_AT : sortBy;
    }

    public Sort.Direction normalizedSortDirection() {
        return sortDirection == null ? Sort.Direction.DESC : sortDirection;
    }
}
