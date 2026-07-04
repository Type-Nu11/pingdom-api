package com.typenull.pingdom.engagement.infrastructure.persistence;


import com.typenull.pingdom.engagement.domain.MapImageLike;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapImageLikeRepository extends JpaRepository<MapImageLike, Long> {

    interface PlaceLikeUserProjection {
        Long getPlaceId();

        Long getUserId();
    }

    Boolean existsByUserIdAndMapImageId(Long userId, Long mapImageId);

    long countByMapImageId(Long mapImageId);

    void deleteByUserIdAndMapImageId(Long userId, Long mapImageId);

    @Modifying
    @Query("""
    DELETE FROM MapImageLike m
    WHERE m.userId = :userId
    AND m.mapImageId = :mapImageId
""")
    void deleteLike(@Param("userId") Long userId, @Param("mapImageId") Long mapImageId);

    @Modifying
    @Query("""
            DELETE FROM MapImageLike m
            WHERE m.userId = :userId
            """)
    int deleteAllByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT image.mapPlace.id
            FROM MapImageLike likeHistory
            JOIN MapImage image ON image.id = likeHistory.mapImageId
            WHERE likeHistory.userId = :userId
              AND image.mapPlace IS NOT NULL
            """)
    List<Long> findPlaceIdsByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT image.mapPlace.id as placeId, likeHistory.userId as userId
            FROM MapImageLike likeHistory
            JOIN MapImage image ON image.id = likeHistory.mapImageId
            WHERE image.mapPlace IS NOT NULL
              AND image.mapPlace.id IN :placeIds
            """)
    List<PlaceLikeUserProjection> findLikeUsersByPlaceIds(@Param("placeIds") Collection<Long> placeIds);

    @Query("SELECT m.mapImageId FROM MapImageLike m WHERE m.userId = :userId AND m.mapImageId IN :mapImageIds")
    Set<Long> findLikedMapImageIdsByUserIdAndMapImageIds(@Param("userId") Long userId, @Param("mapImageIds") Collection<Long> mapImageIds);

    @Query("""
            SELECT m.mapImageId
            FROM MapImageLike m
            WHERE m.userId = :userId
            ORDER BY m.likeId DESC
            """)
    List<Long> findRecentMapImageIdsByUserId(@Param("userId") Long userId, Pageable pageable);
}
