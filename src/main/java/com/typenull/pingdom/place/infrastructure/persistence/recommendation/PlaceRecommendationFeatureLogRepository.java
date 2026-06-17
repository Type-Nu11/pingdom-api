package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationFeatureLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRecommendationFeatureLogRepository extends JpaRepository<PlaceRecommendationFeatureLog, Long> {

    List<PlaceRecommendationFeatureLog> findByRequestIdOrderByRankingAsc(String requestId);
}
