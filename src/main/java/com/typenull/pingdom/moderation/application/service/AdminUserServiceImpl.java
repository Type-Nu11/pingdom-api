package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.ban.BanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserDetailResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserItem;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionHistoryItem;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionStatusResponse;
import com.typenull.pingdom.moderation.application.AdminUserService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserSanctionCommandService userSanctionCommandService;
    private final UserSanctionHistoryRepository userSanctionHistoryRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public BanResponse banUser(Long userId, BanRequest request, Long adminUserId) {
        LocalDateTime now = now();
        User user = findUser(userId);
        LocalDateTime expiresAt = resolveBanExpiresAt(request, now);
        String reason = request == null ? null : request.reason();
        Map<String, Object> beforeState = userSanctionState(user, now);

        userSanctionCommandService.applyBan(user, reason, now, expiresAt, adminUserId);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.USER_BAN_APPLIED,
                AdminAuditTargetType.USER,
                user.getId(),
                reason,
                beforeState,
                userSanctionState(user, now)
        );

        return new BanResponse(
                user.getId(),
                user.isCurrentlyBanned(now),
                user.getBannedAt(),
                user.getBanReason(),
                user.getBanType(),
                user.getBanExpiresAt()
        );
    }

    @Override
    @Transactional
    public UnbanResponse unbanUser(Long userId, UnbanRequest request, Long adminUserId) {
        LocalDateTime now = now();
        User user = findUser(userId);
        String reason = request == null ? null : request.reason();
        Map<String, Object> beforeState = userSanctionState(user, now);

        userSanctionCommandService.releaseBan(user, reason, now, adminUserId);
        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.USER_BAN_RELEASED,
                AdminAuditTargetType.USER,
                user.getId(),
                reason,
                beforeState,
                userSanctionState(user, now)
        );

        return new UnbanResponse(user.getId(), user.isCurrentlyBanned(now), now, reason);
    }

    @Override
    @Transactional
    public AdminBannedUserResponse listBannedUsers(Pageable pageable) {
        int normalizedPage = Math.max(pageable.getPageNumber() + 1, 1);
        int normalizedLimit = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Pageable normalizedPageable = PageRequest.of(
                normalizedPage - 1,
                normalizedLimit,
                Sort.by(Sort.Order.desc("bannedAt"), Sort.Order.desc("id"))
        );

        LocalDateTime now = now();
        Page<User> userPage = userRepository.findAllCurrentlyBanned(
                UserBanType.TEMPORARY,
                now,
                normalizedPageable
        );
        List<AdminBannedUserItem> users = userPage.getContent().stream()
                .map(user -> toItem(user, now))
                .toList();

        return AdminBannedUserResponse.of(
                users,
                normalizedPage,
                normalizedLimit,
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }

    @Override
    @Transactional
    public AdminBannedUserDetailResponse getBannedUser(Long userId) {
        LocalDateTime now = now();
        User user = findUser(userId);
        userSanctionCommandService.expireBanIfNeeded(user, now);
        if (!user.isCurrentlyBanned(now)) {
            throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
        }

        return new AdminBannedUserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBirthYear(),
                user.getLanguage(),
                user.getCountry(),
                user.getRole().name(),
                user.isCurrentlyBanned(now),
                user.getBannedAt(),
                user.getBanType(),
                user.getBanExpiresAt(),
                user.getBanReason(),
                user.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AdminUserSanctionStatusResponse getUserSanctionStatus(Long userId) {
        LocalDateTime now = now();
        User user = findUser(userId);
        userSanctionCommandService.expireBanIfNeeded(user, now);
        boolean currentlyBanned = user.isCurrentlyBanned(now);

        return new AdminUserSanctionStatusResponse(
                user.getId(),
                user.getUsername(),
                currentlyBanned,
                currentlyBanned ? user.getBanType() : null,
                currentlyBanned ? user.getBannedAt() : null,
                currentlyBanned ? user.getBanExpiresAt() : null,
                currentlyBanned ? user.getBanReason() : null
        );
    }

    @Override
    @Transactional
    public AdminUserSanctionHistoryResponse listUserSanctionHistories(
            Long userId,
            UserBanType banType,
            UserSanctionAction action,
            LocalDateTime from,
            LocalDateTime to,
        Pageable pageable
    ) {
        User user = findUser(userId);
        validateHistoryPeriod(from, to);
        userSanctionCommandService.expireBanIfNeeded(user, now());

        int normalizedPage = Math.max(pageable.getPageNumber() + 1, 1);
        int normalizedLimit = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Pageable normalizedPageable = PageRequest.of(
                normalizedPage - 1,
                normalizedLimit,
                Sort.by(Sort.Order.desc("processedAt"), Sort.Order.desc("id"))
        );

        Page<UserSanctionHistory> historyPage = userSanctionHistoryRepository.findByUserIdAndFilters(
                userId,
                banType,
                action,
                from,
                to,
                normalizedPageable
        );
        List<AdminUserSanctionHistoryItem> histories = historyPage.getContent().stream()
                .map(this::toHistoryItem)
                .toList();

        return AdminUserSanctionHistoryResponse.of(
                histories,
                normalizedPage,
                normalizedLimit,
                historyPage.getTotalElements(),
                historyPage.getTotalPages()
        );
    }

    private AdminBannedUserItem toItem(User user, LocalDateTime now) {
        return new AdminBannedUserItem(
                user.getId(),
                user.getUsername(),
                user.isCurrentlyBanned(now),
                user.getBanType(),
                user.getBannedAt(),
                user.getBanExpiresAt()
        );
    }

    private AdminUserSanctionHistoryItem toHistoryItem(UserSanctionHistory history) {
        return new AdminUserSanctionHistoryItem(
                history.getId(),
                history.getTargetUserId(),
                history.getTargetUsername(),
                history.getBanType(),
                history.getAction(),
                history.getReason(),
                history.getStartedAt(),
                history.getEndedAt(),
                history.getAdminUserId(),
                history.getAdminUsername(),
                history.getProcessedAt()
        );
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private LocalDateTime resolveBanExpiresAt(BanRequest request, LocalDateTime now) {
        if (request == null) {
            return null;
        }
        if (request.expiresAt() != null && request.durationDays() != null) {
            throw new AdminException(AdminErrorCode.INVALID_SANCTION_PERIOD);
        }
        if (request.durationDays() != null) {
            return now.plusDays(request.durationDays());
        }
        if (request.expiresAt() == null) {
            return null;
        }
        if (!request.expiresAt().isAfter(now)) {
            throw new AdminException(AdminErrorCode.INVALID_SANCTION_PERIOD);
        }
        return request.expiresAt();
    }

    private void validateHistoryPeriod(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new AdminException(AdminErrorCode.INVALID_SANCTION_FILTER_PERIOD);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Map<String, Object> userSanctionState(User user, LocalDateTime now) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("userId", user.getId());
        state.put("username", user.getUsername());
        state.put("banned", user.isCurrentlyBanned(now));
        state.put("banType", user.getBanType());
        state.put("bannedAt", user.getBannedAt());
        state.put("banExpiresAt", user.getBanExpiresAt());
        state.put("banReason", user.getBanReason());
        return state;
    }
}
