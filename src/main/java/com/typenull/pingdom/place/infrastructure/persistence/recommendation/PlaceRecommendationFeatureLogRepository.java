package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationFeatureLogRepository extends JpaRepository<PlaceRecommendationFeatureLog, Long> {

    List<PlaceRecommendationFeatureLog> findByRequestIdOrderByRankingAsc(String requestId);

    List<PlaceRecommendationFeatureLog> findByRequestIdAndUserIdOrderByRankingAsc(String requestId, Long userId);

    Optional<PlaceRecommendationFeatureLog> findFirstByRequestIdAndUserIdAndPlaceIdAndRecommendationVersionOrderByIdAsc(
            String requestId,
            Long userId,
            Long placeId,
            String recommendationVersion
    );

    @Query("""
            SELECT l.id
            FROM PlaceRecommendationFeatureLog l
            WHERE l.placeId = :placeId
            """)
    List<Long> findIdsByPlaceId(@Param("placeId") Long placeId);

    @Modifying
    @Query("""
            UPDATE PlaceRecommendationFeatureLog l
            SET l.placeId = :targetPlaceId
            WHERE l.placeId = :sourcePlaceId
            """)
    int updatePlaceId(@Param("sourcePlaceId") Long sourcePlaceId, @Param("targetPlaceId") Long targetPlaceId);

    @Modifying
    @Query("""
            UPDATE PlaceRecommendationFeatureLog l
            SET l.placeId = :targetPlaceId
            WHERE l.id IN :featureLogIds
            """)
    int updatePlaceIdForIds(@Param("targetPlaceId") Long targetPlaceId, @Param("featureLogIds") List<Long> featureLogIds);
}
