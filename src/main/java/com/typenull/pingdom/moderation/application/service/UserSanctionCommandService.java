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
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSanctionCommandService {

    private static final long SANCTION_EXPIRATION_LOCK_KEY = 27420260623L;
    private static final Logger log = LoggerFactory.getLogger(UserSanctionCommandService.class);

    private final UserRepository userRepository;
    private final UserSanctionHistoryRepository userSanctionHistoryRepository;
    private final UserAccessStatusService userAccessStatusService;
    private final EntityManager entityManager;
    private final DataSource dataSource;

    private volatile Boolean postgreSQL;

    @Transactional
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

    @Transactional
    public boolean expireBanIfNeeded(User targetUser, LocalDateTime now) {
        if (!targetUser.isBanExpired(now)) {
            return false;
        }

        expireBan(targetUser, now);
        return true;
    }

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

        userSanctionHistoryRepository.save(UserSanctionHistory.builder()
                .targetUserId(targetUser.getId())
                .targetUsername(targetUser.getUsername())
                .banType(previousBanType)
                .action(UserSanctionAction.EXPIRED)
                .reason(previousReason)
                .startedAt(previousStartedAt)
                .endedAt(previousEndedAt)
                .processedAt(now)
                .build());

        userAccessStatusService.evict(targetUser.getId());
    }
}
