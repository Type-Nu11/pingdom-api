package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationExposure;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationExposureRepository extends JpaRepository<PlaceRecommendationExposure, Long> {

    interface PlaceExposureCountProjection {
        Long getPlaceId();

        long getExposureCount();
    }

    interface PlaceVersionExposureCountProjection {
        Long getPlaceId();

        String getRecommendationVersion();

        long getExposureCount();
    }

    @Query("""
            SELECT e.placeId as placeId, COUNT(e) as exposureCount
            FROM PlaceRecommendationExposure e
            WHERE e.placeId IN :placeIds
            GROUP BY e.placeId
            """)
    List<PlaceExposureCountProjection> countExposuresByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    @Query("""
            SELECT e.placeId as placeId, COUNT(e) as exposureCount
            FROM PlaceRecommendationExposure e
            WHERE e.placeId IN :placeIds
              AND e.recommendationVersion = :recommendationVersion
            GROUP BY e.placeId
            """)
    List<PlaceExposureCountProjection> countExposuresByPlaceIdsAndRecommendationVersion(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("recommendationVersion") String recommendationVersion
    );

    @Query("""
            SELECT e.placeId as placeId, COUNT(e) as exposureCount
            FROM PlaceRecommendationExposure e
            WHERE e.placeId IN :placeIds
              AND e.createdAt >= :cutoff
            GROUP BY e.placeId
            """)
    List<PlaceExposureCountProjection> countExposuresByPlaceIdsAndCreatedAtGreaterThanEqual(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("""
            SELECT e.placeId as placeId, COUNT(e) as exposureCount
            FROM PlaceRecommendationExposure e
            WHERE e.placeId IN :placeIds
              AND e.recommendationVersion = :recommendationVersion
              AND e.createdAt >= :cutoff
            GROUP BY e.placeId
            """)
    List<PlaceExposureCountProjection> countExposuresByPlaceIdsAndRecommendationVersionAndCreatedAtGreaterThanEqual(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("recommendationVersion") String recommendationVersion,
            @Param("cutoff") LocalDateTime cutoff
    );

    long countByRecommendationVersion(String recommendationVersion);

    @Query("""
            SELECT e.placeId as placeId,
                   e.recommendationVersion as recommendationVersion,
                   COUNT(e) as exposureCount
            FROM PlaceRecommendationExposure e
            WHERE e.placeId IN :placeIds
            GROUP BY e.placeId, e.recommendationVersion
            """)
    List<PlaceVersionExposureCountProjection> countExposuresByPlaceIdsGroupedByPlaceIdAndRecommendationVersion(
            @Param("placeIds") Collection<Long> placeIds
    );

    @Query("""
            SELECT e.placeId as placeId,
                   e.recommendationVersion as recommendationVersion,
                   COUNT(e) as exposureCount
            FROM PlaceRecommendationExposure e
            GROUP BY e.placeId, e.recommendationVersion
            """)
    List<PlaceVersionExposureCountProjection> countExposuresGroupedByPlaceIdAndRecommendationVersion();

    @Query("""
            SELECT e.id
            FROM PlaceRecommendationExposure e
            WHERE e.placeId = :placeId
            """)
    List<Long> findIdsByPlaceId(@Param("placeId") Long placeId);

    @Modifying
    @Query("""
            UPDATE PlaceRecommendationExposure e
            SET e.placeId = :targetPlaceId
            WHERE e.placeId = :sourcePlaceId
            """)
    int updatePlaceId(@Param("sourcePlaceId") Long sourcePlaceId, @Param("targetPlaceId") Long targetPlaceId);

    @Modifying
    @Query("""
            UPDATE PlaceRecommendationExposure e
            SET e.placeId = :targetPlaceId
            WHERE e.id IN :exposureIds
            """)
    int updatePlaceIdForIds(@Param("targetPlaceId") Long targetPlaceId, @Param("exposureIds") List<Long> exposureIds);
}
