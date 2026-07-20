package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustScoreAnomalyRepository extends JpaRepository<TrustScoreAnomaly, Long> {

    List<TrustScoreAnomaly> findByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(Long reporterUserId);
}
