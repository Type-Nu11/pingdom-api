package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustScoreChangeHistoryRepository extends JpaRepository<TrustScoreChangeHistory, Long> {

    Page<TrustScoreChangeHistory> findAllByReporterUserId(Long reporterUserId, Pageable pageable);
}
