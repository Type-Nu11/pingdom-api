package com.typenull.pingdom.domain.map.repository;


import com.typenull.pingdom.domain.map.domain.MapImageLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapImageLikeRepository extends JpaRepository<MapImageLike, Long> {

    boolean existsByUserIdAndMapImageId(Long userId, Long mapImageId);

    long countByMapImageId(Long mapImageId);

    void deleteByUserIdAndMapImageId(Long userId, Long mapImageId);
}
