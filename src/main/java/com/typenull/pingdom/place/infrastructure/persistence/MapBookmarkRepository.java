package com.typenull.pingdom.place.infrastructure.persistence;

import com.typenull.pingdom.place.domain.MapBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapBookmarkRepository extends JpaRepository<MapBookmark, Long> {

    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

    void deleteByPlaceIdAndUserId(Long placeId, Long userId);
}
