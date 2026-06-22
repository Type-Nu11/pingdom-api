package com.typenull.pingdom.identity.application.service;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawnUserPurgeService {

    private static final long PURGE_LOCK_KEY = 27420260622L;

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;
    private final UserWithdrawalDataService userWithdrawalDataService;
    private final UserWithdrawalProperties properties;
    private final EntityManager entityManager;
    private final DataSource dataSource;

    @Transactional
    public int purgeExpiredUsers(LocalDateTime now) {
        if (!tryAcquirePurgeLock()) {
            log.debug("다른 인스턴스가 탈퇴 사용자 최종 삭제 배치를 실행 중입니다.");
            return 0;
        }

        LocalDateTime cutoff = now.minus(properties.retention());
        List<Long> expiredUserIds = userRepository.findExpiredWithdrawnUserIds(
                UserStatus.WITHDRAWN,
                cutoff,
                PageRequest.of(0, properties.cleanupBatchSize())
        );

        if (expiredUserIds.isEmpty()) {
            return 0;
        }

        userWithdrawalDataService.detachContentUserReferences(expiredUserIds);
        int deletedOAuthAccountCount = oAuthAccountRepository.deleteAllByUserIds(expiredUserIds);
        userRepository.deleteAllByIdInBatch(expiredUserIds);

        log.info(
                "보존기간이 만료된 탈퇴 사용자를 최종 삭제했습니다. userCount={}, deletedOAuthAccountCount={}, cutoff={}",
                expiredUserIds.size(),
                deletedOAuthAccountCount,
                cutoff
        );
        return expiredUserIds.size();
    }

    private boolean tryAcquirePurgeLock() {
        if (!isPostgreSQL()) {
            return true;
        }

        Object result = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(?1)")
                .setParameter(1, PURGE_LOCK_KEY)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }

    private boolean isPostgreSQL() {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            return databaseProductName != null
                    && databaseProductName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException exception) {
            log.warn("데이터베이스 종류를 확인하지 못해 탈퇴 사용자 삭제 배치 분산 락을 생략합니다.", exception);
            return false;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
