package com.typenull.pingdom.reservation.infrastructure;

import com.typenull.pingdom.reservation.domain.Reservation;
import com.typenull.pingdom.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("""
            select count(reservation)
            from Reservation reservation
            join PlaceAvailability availability on availability.id = reservation.availabilityId
            where availability.merchantOwnerUserId = :ownerId
            """)
    long countOwnedByMerchantOwnerUserId(@Param("ownerId") Long ownerId);

    @Query("""
            select count(reservation)
            from Reservation reservation
            join PlaceAvailability availability on availability.id = reservation.availabilityId
            where availability.merchantOwnerUserId = :ownerId
              and reservation.status = :status
            """)
    long countOwnedByMerchantOwnerUserIdAndStatus(
            @Param("ownerId") Long ownerId,
            @Param("status") ReservationStatus status
    );

    Optional<Reservation> findByTouristUserIdAndIdempotencyKey(Long touristUserId, String idempotencyKey);

    Page<Reservation> findAllByTouristUserId(Long touristUserId, Pageable pageable);

    @Query("""
            select reservation from Reservation reservation
            join PlaceAvailability availability on availability.id = reservation.availabilityId
            where exists (
                select ownerPlace.placeId from MerchantOwnerPlace ownerPlace
                where ownerPlace.placeId = availability.placeId
                  and ownerPlace.merchantOwnerUserId = :ownerId
            )
            """)
    Page<Reservation> findAllOwned(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("""
            select reservation from Reservation reservation
            join PlaceAvailability availability on availability.id = reservation.availabilityId
            where (:status is null or reservation.status = :status)
              and (:placeId is null or availability.placeId = :placeId)
              and (:ownerId is null or availability.merchantOwnerUserId = :ownerId)
              and (:touristUserId is null or reservation.touristUserId = :touristUserId)
              and (:productId is null or reservation.productId = :productId)
              and (:hasReservationFrom = false or reservation.reservationStartsAt >= :reservationFrom)
              and (:hasReservationTo = false or reservation.reservationStartsAt < :reservationTo)
            """)
    Page<Reservation> findAllForAdmin(@Param("status") ReservationStatus status, @Param("placeId") Long placeId,
            @Param("ownerId") Long ownerId, @Param("touristUserId") Long touristUserId,
            @Param("productId") Long productId, @Param("hasReservationFrom") boolean hasReservationFrom,
            @Param("reservationFrom") java.time.LocalDateTime reservationFrom,
            @Param("hasReservationTo") boolean hasReservationTo,
            @Param("reservationTo") java.time.LocalDateTime reservationTo, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation where reservation.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);
}
