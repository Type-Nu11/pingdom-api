package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRecommendationClickRepository extends JpaRepository<PlaceRecommendationClick, Long> {

    interface PlaceClickCountProjection {
        Long getPlaceId();

        long getClickCount();
    }

    @Query("""
            SELECT c.placeId as placeId, COUNT(c) as clickCount
            FROM PlaceRecommendationClick c
            WHERE c.placeId IN :placeIds
            GROUP BY c.placeId
            """)
    List<PlaceClickCountProjection> countClicksByPlaceIds(@Param("placeIds") Collection<Long> placeIds);
}
