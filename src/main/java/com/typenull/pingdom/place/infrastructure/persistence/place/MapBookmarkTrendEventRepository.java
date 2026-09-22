package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 북마크 제거 이력 저장과 장소 병합·복구 시 집계 대상 장소의 재지정을 제공합니다.
 * originPlaceId는 변경하지 않아 복구할 때 원래 장소에서 온 이벤트만 골라 되돌릴 수 있습니다.
 */
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
