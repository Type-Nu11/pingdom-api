package com.typenull.pingdom.moderation.application;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.api.dto.ban.BanRequest;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserDetailResponse;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserSearchCondition;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionStatusResponse;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    BanResponse banUser(Long userId, BanRequest request, Long adminUserId);

    UnbanResponse unbanUser(Long userId, UnbanRequest request, Long adminUserId);

    AdminBannedUserResponse listBannedUsers(AdminBannedUserSearchCondition condition, Pageable pageable);

    AdminBannedUserDetailResponse getBannedUser(Long userId);

    AdminUserSanctionStatusResponse getUserSanctionStatus(Long userId);

    AdminUserSanctionHistoryResponse listUserSanctionHistories(
            Long userId,
            UserBanType banType,
            UserSanctionAction action,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    );
}
