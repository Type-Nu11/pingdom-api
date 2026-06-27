package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationVersionSnapshot;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationVersionSnapshotRepository
        extends JpaRepository<PlaceRecommendationVersionSnapshot, Long> {

    Optional<PlaceRecommendationVersionSnapshot> findByPlaceIdAndRecommendationVersion(
            Long placeId,
            String recommendationVersion
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT s
            FROM PlaceRecommendationVersionSnapshot s
            WHERE s.placeId = :placeId
              AND s.recommendationVersion = :recommendationVersion
            """)
    Optional<PlaceRecommendationVersionSnapshot> findByPlaceIdAndRecommendationVersionForReadLock(
            @Param("placeId") Long placeId,
            @Param("recommendationVersion") String recommendationVersion
    );

    List<PlaceRecommendationVersionSnapshot> findByPlaceIdInAndRecommendationVersion(
            Collection<Long> placeIds,
            String recommendationVersion
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT s
            FROM PlaceRecommendationVersionSnapshot s
            WHERE s.placeId IN :placeIds
              AND s.recommendationVersion = :recommendationVersion
            """)
    List<PlaceRecommendationVersionSnapshot> findByPlaceIdInAndRecommendationVersionForReadLock(
            @Param("placeIds") Collection<Long> placeIds,
            @Param("recommendationVersion") String recommendationVersion
    );

    List<PlaceRecommendationVersionSnapshot> findByPlaceIdIn(Collection<Long> placeIds);

    @Query("""
            SELECT COALESCE(SUM(s.clickCount), 0)
            FROM PlaceRecommendationVersionSnapshot s
            WHERE s.recommendationVersion = :recommendationVersion
            """)
    Long sumClickCountByRecommendationVersion(@Param("recommendationVersion") String recommendationVersion);

    @Query("""
            SELECT COALESCE(SUM(s.exposureCount), 0)
            FROM PlaceRecommendationVersionSnapshot s
            WHERE s.recommendationVersion = :recommendationVersion
            """)
    Long sumExposureCountByRecommendationVersion(@Param("recommendationVersion") String recommendationVersion);
}
