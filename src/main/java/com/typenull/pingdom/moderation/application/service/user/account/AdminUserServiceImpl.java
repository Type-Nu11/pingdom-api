package com.typenull.pingdom.moderation.application.service.user.account;

import com.typenull.pingdom.moderation.application.service.audit.AdminAuditLogService;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.api.dto.ban.BanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.BanResponse;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanRequest;
import com.typenull.pingdom.moderation.api.dto.ban.UnbanResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserCounts;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserDetailResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserItem;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminBannedUserSearchCondition;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionHistoryItem;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionHistoryResponse;
import com.typenull.pingdom.moderation.api.dto.user.AdminUserSanctionStatusResponse;
import com.typenull.pingdom.moderation.application.AdminUserService;
import com.typenull.pingdom.identity.application.service.admin.AdminRoleAuthorizationService;
import com.typenull.pingdom.identity.domain.admin.AdminPermission;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.user.AdminBannedUserSortBy;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USER_READ·USER_SANCTION 권한을 구분하여 사용자 제재 조회·변경을 조정.
 * 제재 상태·이력 조회 일부는 만료된 정지를 정리하므로 쓰기 트랜잭션으로 실행.
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserSanctionCommandService userSanctionCommandService;
    private final UserSanctionHistoryRepository userSanctionHistoryRepository;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminRoleAuthorizationService authorizationService;
    private final Clock clock;

    /**
     * 종료 시각과 일수 중 하나만 허용하고 둘 다 없으면 무기한 정지.
     * 정지 적용·이력·감사 기록을 저장하며, 이미 정지된 사용자의 기존 기간과 사유도 새 값으로 교체.
     */
    @Override
    @Transactional
    public BanResponse banUser(Long userId, BanRequest request, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_SANCTION);
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

    /**
     * USER_SANCTION 권한과 사용자 존재를 확인하고 저장된 정지 상태를 해제해 결과를 반환.
     * 정지 중이 아니면 USER_NOT_BANNED이며 해제 이력·관리자 알림 outbox·감사 기록은 같은 트랜잭션에 저장하고 접근 캐시는 즉시 비움.
     */
    @Override
    @Transactional
    public UnbanResponse unbanUser(Long userId, UnbanRequest request, Long adminUserId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_SANCTION);
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

    /**
     * 현재 정지 중인 계정 목록에 검색어·유형·시작 시각 필터를 적용.
     * 응답의 유형별 counts는 검색어만 공유하므로 기간·유형으로 좁힌 목록의 totalCount와 다를 수 있음.
     */
    @Override
    @Transactional
    public AdminBannedUserResponse listBannedUsers(
            Long adminUserId,
            AdminBannedUserSearchCondition condition,
            Pageable pageable
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_READ);
        int normalizedPage = Math.max(pageable.getPageNumber() + 1, 1);
        int normalizedLimit = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        validateHistoryPeriod(condition.bannedFrom(), condition.bannedTo());
        Pageable normalizedPageable = PageRequest.of(
                normalizedPage - 1,
                normalizedLimit,
                bannedUserSort(condition)
        );

        LocalDateTime now = now();
        String normalizedKeyword = condition.normalizedKeyword();
        Page<User> userPage = userRepository.findAllCurrentlyBanned(
                UserBanType.TEMPORARY,
                now,
                normalizedKeyword,
                condition.isNumericKeyword(),
                condition.banType(),
                condition.bannedFrom() != null,
                condition.bannedFrom(),
                condition.bannedTo() != null,
                condition.bannedTo(),
                normalizedPageable
        );
        List<AdminBannedUserItem> users = userPage.getContent().stream()
                .map(user -> toItem(user, now))
                .toList();
        CurrentBannedUserCounts counts = userRepository.countCurrentlyBannedByType(
                UserBanType.PERMANENT,
                UserBanType.TEMPORARY,
                now,
                normalizedKeyword
        );

        return AdminBannedUserResponse.of(
                users,
                normalizedPage,
                normalizedLimit,
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                toCounts(counts)
        );
    }

    /**
     * USER_READ 권한을 확인하고 만료 정리 후에도 현재 정지 중인 사용자만 상세 정보를 반환.
     * 사용자가 없거나 현재 정지가 아니면 USER_NOT_FOUND이며 만료 정리 후 이 오류가 발생하면 같은 트랜잭션의 DB 변경도 롤백.
     */
    @Override
    @Transactional
    public AdminBannedUserDetailResponse getBannedUser(Long adminUserId, Long userId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_READ);
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

    /**
     * USER_READ 권한과 사용자 존재를 확인하고 만료된 정지를 정리한 뒤 현재 제재 정보를 반환.
     * 만료 시 상태·이력·알림 outbox도 바뀌는 쓰기 조회이며 현재 정지가 아니면 제재 유형·기간·사유는 null.
     */
    @Override
    @Transactional
    public AdminUserSanctionStatusResponse getUserSanctionStatus(Long adminUserId, Long userId) {
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_READ);
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

    /**
     * USER_READ 권한과 사용자·기간 조건을 확인하고 만료 정지를 정리한 뒤 제재 이력을 최신 처리순으로 반환.
     * 유형·조치·처리 시각의 양 끝을 포함해 필터링하고 페이지 크기는 1~100으로 보정. 만료 시 새 이력·알림 outbox가 저장될 수 있음.
     */
    @Override
    @Transactional
    public AdminUserSanctionHistoryResponse listUserSanctionHistories(
            Long adminUserId,
            Long userId,
            UserBanType banType,
            UserSanctionAction action,
            LocalDateTime from,
            LocalDateTime to,
        Pageable pageable
    ) {
        authorizationService.requirePermission(adminUserId, AdminPermission.USER_READ);
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

        Page<UserSanctionHistory> historyPage = userSanctionHistoryRepository.findAll(
                sanctionHistorySpecification(userId, banType, action, from, to),
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

    private AdminBannedUserCounts toCounts(CurrentBannedUserCounts counts) {
        return new AdminBannedUserCounts(
                counts.total(),
                counts.permanent(),
                counts.temporary()
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

    private Specification<UserSanctionHistory> sanctionHistorySpecification(
            Long userId,
            UserBanType banType,
            UserSanctionAction action,
            LocalDateTime from,
            LocalDateTime to
    ) {
        Specification<UserSanctionHistory> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("targetUserId"), userId);

        if (banType != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("banType"), banType));
        }
        if (action != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("action"), action));
        }
        if (from != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("processedAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("processedAt"), to));
        }

        return specification;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private LocalDateTime resolveBanExpiresAt(BanRequest request, LocalDateTime now) {
        if (request == null) {
            return null;
        }
        // 적용 기준의 모호함을 방지하기 위해 절대 종료 시각·상대 기간 동시 입력 거절.
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

    private Sort bannedUserSort(AdminBannedUserSearchCondition condition) {
        return switch (condition.normalizedSortBy()) {
            case EXPIRES_AT -> Sort.by(
                    new Sort.Order(condition.normalizedSortDirection(), "banExpiresAt"),
                    new Sort.Order(condition.normalizedSortDirection(), "id")
            );
            case USER_ID -> Sort.by(new Sort.Order(condition.normalizedSortDirection(), "id"));
            case BANNED_AT -> Sort.by(
                    new Sort.Order(condition.normalizedSortDirection(), "bannedAt"),
                    new Sort.Order(condition.normalizedSortDirection(), "id")
            );
        };
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
