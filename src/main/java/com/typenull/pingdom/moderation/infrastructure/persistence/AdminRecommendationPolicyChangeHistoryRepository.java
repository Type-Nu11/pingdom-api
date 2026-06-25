package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.recommendation.AdminRecommendationPolicyChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRecommendationPolicyChangeHistoryRepository
        extends JpaRepository<AdminRecommendationPolicyChangeHistory, Long> {
}
