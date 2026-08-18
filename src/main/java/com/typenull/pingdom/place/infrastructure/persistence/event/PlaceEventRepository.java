package com.typenull.pingdom.place.infrastructure.persistence.event;

import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PlaceEventRepository extends JpaRepository<PlaceEvent, Long> {

    @EntityGraph(attributePaths = "place")
    @Query(value = """
            SELECT e FROM PlaceEvent e
            WHERE (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(e.place.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:placeId IS NULL OR e.place.id = :placeId)
              AND (:eventType IS NULL OR e.eventType = :eventType)
              AND (:publicationStatus IS NULL OR e.publicationStatus = :publicationStatus)
              AND (:scheduleStatus IS NULL OR
                   (:scheduleStatus = com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus.UPCOMING AND e.startAt > :now) OR
                   (:scheduleStatus = com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus.ONGOING AND e.startAt <= :now AND e.endAt > :now) OR
                   (:scheduleStatus = com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus.ENDED AND e.endAt <= :now))
            """,
            countQuery = """
            SELECT COUNT(e) FROM PlaceEvent e
            WHERE (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(e.place.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:placeId IS NULL OR e.place.id = :placeId)
              AND (:eventType IS NULL OR e.eventType = :eventType)
              AND (:publicationStatus IS NULL OR e.publicationStatus = :publicationStatus)
              AND (:scheduleStatus IS NULL OR
                   (:scheduleStatus = com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus.UPCOMING AND e.startAt > :now) OR
                   (:scheduleStatus = com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus.ONGOING AND e.startAt <= :now AND e.endAt > :now) OR
                   (:scheduleStatus = com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus.ENDED AND e.endAt <= :now))
            """)
    Page<PlaceEvent> findAdminEvents(
            @Param("keyword") String keyword, @Param("placeId") Long placeId,
            @Param("eventType") PlaceEventType eventType,
            @Param("publicationStatus") PlaceEventPublicationStatus publicationStatus,
            @Param("scheduleStatus") PlaceEventScheduleStatus scheduleStatus,
            @Param("now") LocalDateTime now, Pageable pageable);

    boolean existsByPlace_Id(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM PlaceEvent e WHERE e.id = :eventId")
    Optional<PlaceEvent> findByIdForUpdate(@Param("eventId") Long eventId);

    @EntityGraph(attributePaths = "place")
    @Query(
            value = """
                    SELECT e
                    FROM PlaceEvent e
                    WHERE e.publicationStatus = :publicationStatus
                      AND e.endAt > :now
                      AND (:eventType IS NULL OR e.eventType = :eventType)
                      AND (:fromAt IS NULL OR e.endAt > :fromAt)
                      AND (:toAt IS NULL OR e.startAt < :toAt)
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM PlaceEvent e
                    WHERE e.publicationStatus = :publicationStatus
                      AND e.endAt > :now
                      AND (:eventType IS NULL OR e.eventType = :eventType)
                      AND (:fromAt IS NULL OR e.endAt > :fromAt)
                      AND (:toAt IS NULL OR e.startAt < :toAt)
                    """
    )
    Page<PlaceEvent> findDiscoverableEvents(
            @Param("publicationStatus") PlaceEventPublicationStatus publicationStatus,
            @Param("now") LocalDateTime now,
            @Param("eventType") PlaceEventType eventType,
            @Param("fromAt") LocalDateTime fromAt,
            @Param("toAt") LocalDateTime toAt,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "place")
    Optional<PlaceEvent> findByIdAndPublicationStatusAndEndAtAfter(
            Long id,
            PlaceEventPublicationStatus publicationStatus,
            LocalDateTime now
    );

    @Query("""
            SELECT event
            FROM PlaceEvent event
            WHERE event.place.id = :placeId
              AND event.publicationStatus = :publicationStatus
              AND event.startAt <= :now
              AND event.endAt > :now
            ORDER BY event.startAt ASC, event.id ASC
            """)
    List<PlaceEvent> findOngoingPublishedByPlaceId(
            @Param("placeId") Long placeId,
            @Param("publicationStatus") PlaceEventPublicationStatus publicationStatus,
            @Param("now") LocalDateTime now
    );
}
