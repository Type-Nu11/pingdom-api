package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapBookmarkTrendEventRepository extends JpaRepository<MapBookmarkTrendEvent, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MapBookmarkTrendEvent event WHERE event.placeId = :placeId")
    int deleteAllByPlaceId(@Param("placeId") Long placeId);

    @Modifying
    @Query("UPDATE MapBookmarkTrendEvent event SET event.placeId = :targetPlaceId WHERE event.placeId = :sourcePlaceId")
    int reassignPlace(@Param("sourcePlaceId") Long sourcePlaceId, @Param("targetPlaceId") Long targetPlaceId);

    @Modifying
    @Query("""
            UPDATE MapBookmarkTrendEvent event
            SET event.placeId = :restoredSourcePlaceId
            WHERE event.originPlaceId = :originalSourcePlaceId
              AND event.placeId = :targetPlaceId
            """)
    int restoreOriginalPlace(
            @Param("originalSourcePlaceId") Long originalSourcePlaceId,
            @Param("targetPlaceId") Long targetPlaceId,
            @Param("restoredSourcePlaceId") Long restoredSourcePlaceId
    );

    default void recordRemovals(Long userId, List<Long> placeIds, java.time.LocalDateTime occurredAt) {
        saveAll(placeIds.stream()
                .map(placeId -> MapBookmarkTrendEvent.removed(userId, placeId, occurredAt))
                .toList());
    }
}
