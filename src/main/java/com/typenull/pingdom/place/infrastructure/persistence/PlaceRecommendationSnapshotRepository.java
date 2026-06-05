package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRecommendationSnapshotRepository extends JpaRepository<PlaceRecommendationSnapshot, Long> {
    List<PlaceRecommendationSnapshot> findByPlaceIdIn(Collection<Long> placeIds);
}
