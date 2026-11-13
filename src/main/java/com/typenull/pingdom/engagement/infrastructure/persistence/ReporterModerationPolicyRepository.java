package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReporterModerationPolicyRepository extends JpaRepository<ReporterModerationPolicy, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT policy FROM ReporterModerationPolicy policy WHERE policy.reporterUserId = :reporterUserId")
    Optional<ReporterModerationPolicy> findByReporterUserIdForUpdate(@Param("reporterUserId") Long reporterUserId);

    @Query("SELECT MAX(policy.reporterUserId) FROM ReporterModerationPolicy policy")
    Long findMaxReporterUserId();

    @Query("""
            SELECT policy.reporterUserId FROM ReporterModerationPolicy policy
            WHERE (:afterId IS NULL OR policy.reporterUserId > :afterId)
              AND policy.reporterUserId <= :throughId
            ORDER BY policy.reporterUserId
            """)
    List<Long> findReporterUserIdsAfter(@Param("afterId") Long afterId,
                                       @Param("throughId") Long throughId,
                                       Pageable pageable);

    // 없는 정책의 동시 생성은 PK 충돌로 호출 트랜잭션을 실패시키지 않고, 이후 잠금 조회로 직렬화합니다.
    @Modifying
    @Query(value = """
            INSERT INTO reporter_moderation_policy (
                reporter_user_id, reporter_username, submitted_count, accepted_count,
                declined_count, false_report_count, trust_score, created_at, updated_at
            ) VALUES (:reporterUserId, :reporterUsername, 0, 0, 0, 0, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (reporter_user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("reporterUserId") Long reporterUserId,
                       @Param("reporterUsername") String reporterUsername);
}
