package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @Query("""
            SELECT log
            FROM AdminAuditLog log
            WHERE (:actorUserId IS NULL OR log.actorUserId = :actorUserId)
              AND (:action IS NULL OR log.action = :action)
              AND (:targetType IS NULL OR log.targetType = :targetType)
              AND (:targetId IS NULL OR log.targetId = :targetId)
              AND (:from IS NULL OR log.createdAt >= :from)
              AND (:to IS NULL OR log.createdAt <= :to)
            """)
    Page<AdminAuditLog> findByFilters(
            @Param("actorUserId") Long actorUserId,
            @Param("action") AdminAuditAction action,
            @Param("targetType") AdminAuditTargetType targetType,
            @Param("targetId") String targetId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
