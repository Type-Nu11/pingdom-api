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

/**
 * 행위자·작업·대상은 선택적 일치 조건, 생성 시각은 시작 이상·종료 이하로 감사 기록을 조회.
 * 시각 존재 여부 플래그와 실제 인자를 함께 전달해야 하며 권한 확인은 호출 서비스가 수행.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @Query("""
            SELECT log
            FROM AdminAuditLog log
            WHERE (:actorUserId IS NULL OR log.actorUserId = :actorUserId)
              AND (:action IS NULL OR log.action = :action)
              AND (:targetType IS NULL OR log.targetType = :targetType)
              AND (:targetId IS NULL OR log.targetId = :targetId)
              AND (:hasFrom = false OR log.createdAt >= :from)
              AND (:hasTo = false OR log.createdAt <= :to)
            """)
    Page<AdminAuditLog> findByFilters(
            @Param("actorUserId") Long actorUserId,
            @Param("action") AdminAuditAction action,
            @Param("targetType") AdminAuditTargetType targetType,
            @Param("targetId") String targetId,
            @Param("hasFrom") boolean hasFrom,
            @Param("from") LocalDateTime from,
            @Param("hasTo") boolean hasTo,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
