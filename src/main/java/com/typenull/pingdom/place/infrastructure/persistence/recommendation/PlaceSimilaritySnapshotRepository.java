package com.typenull.pingdom.place.infrastructure.persistence.recommendation;

import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.query.Param;

/**
 * 선택 장소 집합 안의 쌍 또는 한 장소가 포함된 유사도 쌍을 조회합니다.
 * 기존 쌍 순회는 id가 lastSeenId보다 큰 행을 오름차순으로 읽는 커서 방식입니다.
 */
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

    @Query("""
            SELECT s
            FROM PlaceSimilaritySnapshot s
            WHERE s.leftPlaceId = :placeId
               OR s.rightPlaceId = :placeId
            ORDER BY s.id ASC
            """)
    List<PlaceSimilaritySnapshot> findByPlaceId(@Param("placeId") Long placeId);
}
