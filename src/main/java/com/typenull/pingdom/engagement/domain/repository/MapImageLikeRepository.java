package com.typenull.pingdom.engagement.domain.repository;


import com.typenull.pingdom.engagement.domain.MapImageLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapImageLikeRepository extends JpaRepository<MapImageLike, Long> {

    boolean existsByUserIdAndMapImageId(Long userId, Long mapImageId);

    long countByMapImageId(Long mapImageId);

    void deleteByUserIdAndMapImageId(Long userId, Long mapImageId);

    @Modifying
    @Query("""
    DELETE FROM MapImageLike m
    WHERE m.userId = :userId
    AND m.mapImageId = :mapImageId
""")
    void deleteLike(@Param("userId") Long userId, @Param("mapImageId") Long mapImageId);
}
