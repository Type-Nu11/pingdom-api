package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.ban.BanResponse;

public interface AdminUserService {
    BanResponse banUser(Long userId, String reason);
}

