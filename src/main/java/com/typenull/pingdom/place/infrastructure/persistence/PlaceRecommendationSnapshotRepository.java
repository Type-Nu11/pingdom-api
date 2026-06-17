package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaceRecommendationSnapshotRepository extends JpaRepository<PlaceRecommendationSnapshot, Long> {
    List<PlaceRecommendationSnapshot> findByPlaceIdIn(Collection<Long> placeIds);

    @Query("""
            SELECT COALESCE(SUM(s.clickCount), 0)
            FROM PlaceRecommendationSnapshot s
            """)
    Long sumClickCount();

    @Query("""
            SELECT COALESCE(SUM(s.exposureCount), 0)
            FROM PlaceRecommendationSnapshot s
            """)
    Long sumExposureCount();
}
