package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationVersionSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationVersionSnapshotRepository
        extends JpaRepository<PlaceRecommendationVersionSnapshot, Long> {

    Optional<PlaceRecommendationVersionSnapshot> findByPlaceIdAndRecommendationVersion(
            Long placeId,
            String recommendationVersion
    );

    List<PlaceRecommendationVersionSnapshot> findByPlaceIdInAndRecommendationVersion(
            Collection<Long> placeIds,
            String recommendationVersion
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
