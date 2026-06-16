package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

public interface PlaceRecommendationSnapshotRepository extends JpaRepository<PlaceRecommendationSnapshot, Long> {
    List<PlaceRecommendationSnapshot> findByPlaceIdIn(Collection<Long> placeIds);

    Page<PlaceRecommendationSnapshot> findByUpdatedAtGreaterThanEqual(LocalDateTime updatedAt, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(s.clickCount), 0)
            FROM PlaceRecommendationSnapshot s
            """)
    long sumClickCount();

    @Query("""
            SELECT COALESCE(SUM(s.exposureCount), 0)
            FROM PlaceRecommendationSnapshot s
            """)
    long sumExposureCount();
}
