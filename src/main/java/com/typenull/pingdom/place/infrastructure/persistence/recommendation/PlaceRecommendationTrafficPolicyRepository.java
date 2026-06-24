package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRecommendationTrafficPolicyRepository extends JpaRepository<PlaceRecommendationTrafficPolicy, String> {
}
