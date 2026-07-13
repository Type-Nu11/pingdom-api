package com.typenull.pingdom.place.infrastructure.persistence.event;

import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
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

public interface PlaceEventRepository extends JpaRepository<PlaceEvent, Long> {

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
}
