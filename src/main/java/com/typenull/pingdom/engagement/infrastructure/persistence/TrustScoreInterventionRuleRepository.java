package com.typenull.pingdom.engagement.infrastructure.persistence;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrustScoreInterventionRuleRepository extends JpaRepository<TrustScoreInterventionRule, Long> {

    List<TrustScoreInterventionRule> findByEnabledTrueOrderByPriorityAscIdAsc();
}
