package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendTracking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapBookmarkTrendTrackingRepository extends JpaRepository<MapBookmarkTrendTracking, Boolean> {
}
