package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;

public interface AdminUserService {
    BanResponse banUser(Long userId, String reason);

    AdminBannedUserResponse listBannedUsers();
}
