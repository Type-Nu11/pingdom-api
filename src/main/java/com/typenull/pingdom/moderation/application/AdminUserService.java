package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    BanResponse banUser(Long userId, String reason);

    AdminBannedUserResponse listBannedUsers(Pageable pageable);
}
