package com.typenull.pingdom.post.infrastructure.persistence;

import com.typenull.pingdom.post.domain.MapImage;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MapImageRepository extends JpaRepository<MapImage,Long> {

    interface PlaceImageAggregateProjection {
        Long getPlaceId();

        Long getLikeSum();

        LocalDateTime getLatestCreatedAt();
    }

    @Modifying
    @Query("""
    UPDATE MapImage m
    SET m.likeCount = m.likeCount + 1
    WHERE m.id = :imageId
""")
    void increaseLikeCount(@Param("imageId") Long imageId);

    @Modifying
    @Query("""
    UPDATE MapImage m
    SET m.likeCount = m.likeCount - 1
    WHERE m.id = :imageId
    AND m.likeCount > 0
""")
    void decreaseLikeCount(@Param("imageId") Long imageId);

    @EntityGraph(attributePaths = "mapPlace")
    Page<MapImage> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "mapPlace")
    Optional<MapImage> findWithMapPlaceById(Long id);

    long countByMapPlace_Id(Long placeId);

    List<MapImage> findByMapPlace_Id(Long placeId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(m.likeCount), 0)
            FROM MapImage m
            WHERE m.mapPlace.id = :placeId
            """)
    long sumLikeCountByPlaceId(@Param("placeId") Long placeId);

    @Query("""
            SELECT MAX(m.createdAt)
            FROM MapImage m
            WHERE m.mapPlace.id = :placeId
            """)
    LocalDateTime findLatestCreatedAtByPlaceId(@Param("placeId") Long placeId);

    @Query("SELECT COUNT(m) FROM MapImage m WHERE m.mapPlace.id = :placeId AND (:keyword IS NULL OR :keyword = '' OR m.title LIKE %:keyword%)")
    long countByMapPlace_IdAndTitleContaining(
            @Param("placeId") Long placeId,
            @Param("keyword") String keyword
    );

    @Query("SELECT m FROM MapImage m WHERE m.mapPlace.id = :placeId AND (:keyword IS NULL OR :keyword = '' OR m.title LIKE %:keyword%)")
    Page<MapImage> findByMapPlace_IdAndTitleContaining(
            @Param("placeId") Long placeId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT m.mapPlace.id
            FROM MapImage m
            WHERE m.userId = :userId
              AND m.mapPlace IS NOT NULL
            """)
    List<Long> findPlaceIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT m.mapPlace.id as placeId,
                   COALESCE(SUM(m.likeCount), 0) as likeSum,
                   MAX(m.createdAt) as latestCreatedAt
            FROM MapImage m
            WHERE m.mapPlace.id IN :placeIds
            GROUP BY m.mapPlace.id
            """)
    List<PlaceImageAggregateProjection> findPlaceAggregatesByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    boolean existsByUserIdAndMapPlace_Id(Long userId, Long placeId);
}
