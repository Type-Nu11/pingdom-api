package com.typenull.pingdom.identity.application.service.withdrawal;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.application.PrivacyProcessingOutboxPublisher;
import com.typenull.pingdom.privacy.event.PrivacyProcessingBulkEvent;
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
 * 보존 기간이 지난 탈퇴 회원을 한 배치씩 조회해 참조를 정리한 후 물리 삭제합니다.
 * PostgreSQL에서는 트랜잭션 advisory lock을 사용하고 다른 DB나 종류 판별 실패 시에는 락을 생략합니다.
 */
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
    private final PrivacyProcessingOutboxPublisher privacyProcessingOutboxPublisher;

    private volatile Boolean postgreSQL;

    /**
     * 보존 기간이 지난 탈퇴 회원 한 배치의 콘텐츠 참조·OAuth 연결을 정리한 뒤 회원을 물리 삭제하고 대상 수를 반환합니다.
     * PostgreSQL advisory lock을 얻지 못하거나 대상이 없으면 0을 반환하며, 삭제 시 개인정보 DELETED Outbox 기록도 발행합니다.
     */
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
        privacyProcessingOutboxPublisher.publish(PrivacyProcessingBulkEvent.systemAction(
                expiredUserIds,
                PrivacyProcessingAction.DELETED,
                "보존기간 만료에 따른 탈퇴 사용자 최종 삭제"
        ));

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
            log.warn("데이터베이스 종류를 확인하지 못해 탈퇴 사용자 삭제 배치 분산 락을 생략합니다.", exception);
            return null;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
