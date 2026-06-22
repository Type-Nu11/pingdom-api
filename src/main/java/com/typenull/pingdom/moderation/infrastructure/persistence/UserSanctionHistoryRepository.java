package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSanctionHistoryRepository extends JpaRepository<UserSanctionHistory, Long> {

    @Query("""
            SELECT h
            FROM UserSanctionHistory h
            WHERE h.targetUserId = :userId
              AND (:banType IS NULL OR h.banType = :banType)
              AND (:action IS NULL OR h.action = :action)
              AND (:from IS NULL OR h.processedAt >= :from)
              AND (:to IS NULL OR h.processedAt <= :to)
            """)
    Page<UserSanctionHistory> findByUserIdAndFilters(
            @Param("userId") Long userId,
            @Param("banType") UserBanType banType,
            @Param("action") UserSanctionAction action,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
