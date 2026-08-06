package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreChangeHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustScoreChangeHistoryRepository extends JpaRepository<TrustScoreChangeHistory, Long> {

    List<TrustScoreChangeHistory> findTop100ByReporterUserIdOrderByChangedAtDescIdDesc(Long reporterUserId);
}
