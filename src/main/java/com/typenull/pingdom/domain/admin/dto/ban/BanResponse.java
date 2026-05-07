package com.typenull.pingdom.domain.admin.dto.ban;

import java.time.LocalDateTime;

public record BanResponse(
        Long userId,
        boolean banned,
        LocalDateTime bannedAt,
        String reason
) {
}

