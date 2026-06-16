package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.PlaceSimilaritySnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceSimilaritySnapshotRepository extends JpaRepository<PlaceSimilaritySnapshot, Long> {

    @Query("""
            SELECT s
            FROM PlaceSimilaritySnapshot s
            WHERE s.leftPlaceId IN :placeIds
              AND s.rightPlaceId IN :placeIds
            """)
    List<PlaceSimilaritySnapshot> findByPlaceIdsWithin(@Param("placeIds") Collection<Long> placeIds);
}
