package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.MapFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapFavoriteRepository extends JpaRepository<MapFavorite, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);
}

