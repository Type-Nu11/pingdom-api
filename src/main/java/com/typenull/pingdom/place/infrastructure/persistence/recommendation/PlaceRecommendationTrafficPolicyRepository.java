package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationTrafficPolicy;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PlaceRecommendationTrafficPolicyRepository extends JpaRepository<PlaceRecommendationTrafficPolicy, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT policy FROM PlaceRecommendationTrafficPolicy policy ORDER BY policy.recommendationVersion")
    List<PlaceRecommendationTrafficPolicy> findAllForUpdate();
}
