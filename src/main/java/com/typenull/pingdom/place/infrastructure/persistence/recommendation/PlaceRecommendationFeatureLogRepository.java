package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationFeatureLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationFeatureLogRepository extends JpaRepository<PlaceRecommendationFeatureLog, Long> {

    List<PlaceRecommendationFeatureLog> findByRequestIdOrderByRankingAsc(String requestId);

    @Modifying
    @Query("""
            UPDATE PlaceRecommendationFeatureLog l
            SET l.placeId = :targetPlaceId
            WHERE l.placeId = :sourcePlaceId
            """)
    int updatePlaceId(@Param("sourcePlaceId") Long sourcePlaceId, @Param("targetPlaceId") Long targetPlaceId);
}
