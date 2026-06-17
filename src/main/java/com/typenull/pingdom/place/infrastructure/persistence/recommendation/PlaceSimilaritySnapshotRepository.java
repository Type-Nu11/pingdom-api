package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceSimilaritySnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.query.Param;

public interface PlaceSimilaritySnapshotRepository extends JpaRepository<PlaceSimilaritySnapshot, Long> {

    interface ExistingSnapshotProjection {
        Long getId();

        Long getLeftPlaceId();

        Long getRightPlaceId();
    }

    @Query("""
            SELECT s.id AS id,
                   s.leftPlaceId AS leftPlaceId,
                   s.rightPlaceId AS rightPlaceId
            FROM PlaceSimilaritySnapshot s
            WHERE s.id > :lastSeenId
            ORDER BY s.id ASC
            """)
    Slice<ExistingSnapshotProjection> findExistingSnapshotSlice(@Param("lastSeenId") Long lastSeenId, Pageable pageable);

    @Query("""
            SELECT s
            FROM PlaceSimilaritySnapshot s
            WHERE s.leftPlaceId IN :placeIds
              AND s.rightPlaceId IN :placeIds
            """)
    List<PlaceSimilaritySnapshot> findByPlaceIdsWithin(@Param("placeIds") Collection<Long> placeIds);
}
