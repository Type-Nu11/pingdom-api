package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationClickRepository extends JpaRepository<PlaceRecommendationClick, Long> {

    interface PlaceClickCountProjection {
        Long getPlaceId();

        long getClickCount();
    }

    interface PlaceVersionClickCountProjection {
        Long getPlaceId();

        String getRecommendationVersion();

        long getClickCount();
    }

    @Query("""
            SELECT c.placeId as placeId, COUNT(c) as clickCount
            FROM PlaceRecommendationClick c
            WHERE c.placeId IN :placeIds
            GROUP BY c.placeId
            """)
    List<PlaceClickCountProjection> countClicksByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT c.placeId as placeId, COUNT(c) as clickCount
            FROM PlaceRecommendationClick c
            WHERE c.placeId IN :placeIds
              AND c.recommendationVersion = :recommendationVersion
            GROUP BY c.placeId
            """)
    List<PlaceClickCountProjection> countClicksByPlaceIdsAndRecommendationVersion(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("recommendationVersion") String recommendationVersion
    );

    long countByPlaceId(Long placeId);

    long countByRecommendationVersion(String recommendationVersion);

    @Query("""
            SELECT c.placeId as placeId,
                   c.recommendationVersion as recommendationVersion,
                   COUNT(c) as clickCount
            FROM PlaceRecommendationClick c
            GROUP BY c.placeId, c.recommendationVersion
            """)
    List<PlaceVersionClickCountProjection> countClicksGroupedByPlaceIdAndRecommendationVersion();

    Optional<PlaceRecommendationClick> findFirstByUserIdAndPlaceIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            Long placeId,
            LocalDateTime createdAt
    );
}
