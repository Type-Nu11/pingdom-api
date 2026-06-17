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
            SELECT SUM(s.clickCount)
            FROM PlaceRecommendationSnapshot s
            """)
    Long sumClickCount();

    @Query("""
            SELECT SUM(s.exposureCount)
            FROM PlaceRecommendationSnapshot s
            """)
    Long sumExposureCount();
}
