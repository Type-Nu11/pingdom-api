package com.typenull.pingdom.moderation.application.service.user.sanction;

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
import com.typenull.pingdom.moderation.outbox.notification.AdminNotificationOutboxPublisher;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 정지 상태·제재 이력·관리자 알림 outbox를 변경하고 현재 JVM의 접근 상태 캐시 제거.
 * 호출자의 관리자 업무 권한 검증을 전제로 하며 이 서비스의 관리자 조회는 계정 존재만 확인.
 * 캐시 제거는 DB 커밋과 별개로 즉시 수행하며 무효화 범위는 현재 JVM으로 한정.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSanctionCommandService {

    private static final long SANCTION_EXPIRATION_LOCK_KEY = 27420260623L;

    private final UserRepository userRepository;
    private final UserSanctionHistoryRepository userSanctionHistoryRepository;
    private final UserAccessStatusService userAccessStatusService;
    private final AdminNotificationOutboxPublisher adminNotificationOutboxPublisher;
    private final EntityManager entityManager;
    private final DataSource dataSource;

    private volatile Boolean postgreSQL;

    /** 기존 정지 상태를 새 사유·기간으로 교체하고 refresh token을 비운 뒤 이력과 알림을 저장. 기간·권한 검증은 호출자 책임. */
    @Transactional
    public void applyBan(User targetUser, String reason, LocalDateTime now, LocalDateTime expiresAt, Long adminUserId) {
        User adminUser = findAdminUser(adminUserId);
        targetUser.ban(reason, now, expiresAt);

        UserSanctionHistory history = userSanctionHistoryRepository.save(UserSanctionHistory.builder()
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
        adminNotificationOutboxPublisher.publishUserSanction(
                history.getId(),
                targetUser.getId(),
                history.getAction()
        );

        userAccessStatusService.evict(targetUser.getId());
    }

    /**
     * 만료 시각과 별도로 저장된 banned 플래그가 참인 사용자만 수동 해제하고 기존 유형·기간을 RELEASED 이력에 보존.
     * 정지 상태가 아니거나 관리자 계정이 없으면 거절하며 상태·이력·알림 outbox는 같은 트랜잭션, 로컬 접근 캐시 제거는 즉시 수행.
     * 관리자의 업무 권한 검증은 호출자가 담당.
     */
    @Transactional
    public void releaseBan(User targetUser, String reason, LocalDateTime now, Long adminUserId) {
        if (!targetUser.isBanned()) {
            throw new AdminException(AdminErrorCode.USER_NOT_BANNED);
        }

        User adminUser = findAdminUser(adminUserId);
        UserBanType previousBanType = targetUser.getBanType();
        LocalDateTime previousStartedAt = targetUser.getBannedAt();
        LocalDateTime previousEndedAt = targetUser.getBanExpiresAt();

        targetUser.releaseBan();

        UserSanctionHistory history = userSanctionHistoryRepository.save(UserSanctionHistory.builder()
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
        adminNotificationOutboxPublisher.publishUserSanction(
                history.getId(),
                targetUser.getId(),
                history.getAction()
        );

        userAccessStatusService.evict(targetUser.getId());
    }

    @Transactional
    public boolean expireBanIfNeeded(User targetUser, LocalDateTime now) {
        if (!targetUser.isBanExpired(now)) {
            return false;
        }

        expireBan(targetUser, now);
        return true;
    }

    /**
     * 한 번에 첫 배치의 만료 정지만 해제. PostgreSQL은 트랜잭션 advisory lock 획득 실패 시 0을 반환하고,
     * 다른 DB 또는 DB 종류 판별 실패 시에는 분산 잠금 없이 실행. 배치 크기 하한은 1.
     */
    @Transactional
    public int expireExpiredTemporaryBans(LocalDateTime now, int batchSize) {
        if (!tryAcquireExpirationLock()) {
            log.debug("다른 인스턴스가 기간 제재 만료 배치를 실행 중입니다.");
            return 0;
        }

        List<User> expiredUsers = userRepository.findExpiredTemporaryBannedUsers(
                UserBanType.TEMPORARY,
                now,
                PageRequest.of(0, Math.max(batchSize, 1))
        );

        expiredUsers.forEach(user -> expireBan(user, now));
        return expiredUsers.size();
    }

    private User findAdminUser(Long adminUserId) {
        if (adminUserId == null) {
            throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
        }
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }

    private boolean tryAcquireExpirationLock() {
        if (!isPostgreSQL()) {
            return true;
        }

        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(?1)")
                .setParameter(1, SANCTION_EXPIRATION_LOCK_KEY)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private boolean isPostgreSQL() {
        Boolean cached = postgreSQL;
        if (cached != null) {
            return cached;
        }

        Boolean detected = detectPostgreSQL();
        if (detected != null) {
            postgreSQL = detected;
            return detected;
        }
        return false;
    }

    private Boolean detectPostgreSQL() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            return databaseProductName != null
                    && databaseProductName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException exception) {
            log.warn("데이터베이스 종류를 확인하지 못해 기간 제재 만료 배치 분산 락을 생략합니다.", exception);
            return null;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void expireBan(User targetUser, LocalDateTime now) {
        UserBanType previousBanType = targetUser.getBanType();
        LocalDateTime previousStartedAt = targetUser.getBannedAt();
        LocalDateTime previousEndedAt = targetUser.getBanExpiresAt();
        String previousReason = targetUser.getBanReason();

        targetUser.releaseBan();

        UserSanctionHistory history = userSanctionHistoryRepository.save(UserSanctionHistory.builder()
                .targetUserId(targetUser.getId())
                .targetUsername(targetUser.getUsername())
                .banType(previousBanType)
                .action(UserSanctionAction.EXPIRED)
                .reason(previousReason)
                .startedAt(previousStartedAt)
                .endedAt(previousEndedAt)
                .processedAt(now)
                .build());
        adminNotificationOutboxPublisher.publishUserSanction(
                history.getId(),
                targetUser.getId(),
                history.getAction()
        );

        userAccessStatusService.evict(targetUser.getId());
    }
}
