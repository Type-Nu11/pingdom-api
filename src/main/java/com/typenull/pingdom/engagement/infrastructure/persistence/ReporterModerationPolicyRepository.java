package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReporterModerationPolicyRepository extends JpaRepository<ReporterModerationPolicy, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT policy FROM ReporterModerationPolicy policy WHERE policy.reporterUserId = :reporterUserId")
    Optional<ReporterModerationPolicy> findByReporterUserIdForUpdate(@Param("reporterUserId") Long reporterUserId);
}
