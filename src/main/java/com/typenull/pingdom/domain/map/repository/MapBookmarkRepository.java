package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.MapBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapBookmarkRepository extends JpaRepository<MapBookmark, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);
}

