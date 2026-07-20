package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustScoreAnomalyRepository extends JpaRepository<TrustScoreAnomaly, Long> {

    List<TrustScoreAnomaly> findByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(Long reporterUserId);

    Page<TrustScoreAnomaly> findAllByReporterUserIdOrderByDetectedAtDescIdDesc(Long reporterUserId, Pageable pageable);

    Page<TrustScoreAnomaly> findAllByReporterUserIdAndResolvedAtIsNullOrderByDetectedAtDescIdDesc(
            Long reporterUserId,
            Pageable pageable
    );

    Page<TrustScoreAnomaly> findAllByResolvedAtIsNullOrderByDetectedAtDescIdDesc(Pageable pageable);

    Page<TrustScoreAnomaly> findAllByOrderByDetectedAtDescIdDesc(Pageable pageable);
}
