package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.shared.security.UserAccessStatusService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSanctionCommandService {

    private final UserRepository userRepository;
    private final UserSanctionHistoryRepository userSanctionHistoryRepository;
    private final UserAccessStatusService userAccessStatusService;

    public void applyBan(User targetUser, String reason, LocalDateTime now, LocalDateTime expiresAt, Long adminUserId) {
        User adminUser = findAdminUser(adminUserId);
        targetUser.ban(reason, now, expiresAt);

        userSanctionHistoryRepository.save(UserSanctionHistory.builder()
                .targetUserId(targetUser.getId())
                .targetUsername(targetUser.getUsername())
                .banType(targetUser.getBanType())
                .action(UserSanctionAction.APPLIED)
                .reason(reason)
                .startedAt(targetUser.getBannedAt())
                .endedAt(targetUser.getBanExpiresAt())
                .adminUserId(adminUser.getId())
                .adminUsername(adminUser.getUsername())
                .processedAt(now)
                .build());

        userAccessStatusService.evict(targetUser.getId());
    }

    public void releaseBan(User targetUser, String reason, LocalDateTime now, Long adminUserId) {
        if (!targetUser.isCurrentlyBanned(now)) {
            throw new AdminException(AdminErrorCode.USER_NOT_BANNED);
        }

        User adminUser = findAdminUser(adminUserId);
        UserBanType previousBanType = targetUser.getBanType();
        LocalDateTime previousStartedAt = targetUser.getBannedAt();
        LocalDateTime previousEndedAt = targetUser.getBanExpiresAt();

        targetUser.releaseBan();

        userSanctionHistoryRepository.save(UserSanctionHistory.builder()
                .targetUserId(targetUser.getId())
                .targetUsername(targetUser.getUsername())
                .banType(previousBanType)
                .action(UserSanctionAction.RELEASED)
                .reason(reason)
                .startedAt(previousStartedAt)
                .endedAt(previousEndedAt)
                .adminUserId(adminUser.getId())
                .adminUsername(adminUser.getUsername())
                .processedAt(now)
                .build());

        userAccessStatusService.evict(targetUser.getId());
    }

    private User findAdminUser(Long adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }
}
