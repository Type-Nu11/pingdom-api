package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationRequest;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlaceInformationReverificationRequestRepository
        extends JpaRepository<PlaceInformationReverificationRequest, Long> {

    @Query("""
            SELECT request FROM PlaceInformationReverificationRequest request
            JOIN MerchantOwnerPlace owner ON owner.placeId = request.place.id
            WHERE owner.merchantOwnerUserId = :userId
            ORDER BY request.requestedAt DESC, request.id DESC
            """)
    Page<PlaceInformationReverificationRequest> findAllForCurrentOwner(@Param("userId") Long userId, Pageable pageable);

    Page<PlaceInformationReverificationRequest> findAllByPlace_IdOrderByRequestedAtDescIdDesc(Long placeId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT request FROM PlaceInformationReverificationRequest request WHERE request.id = :id")
    Optional<PlaceInformationReverificationRequest> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT request FROM PlaceInformationReverificationRequest request
            WHERE request.status = :status AND request.dueAt <= :now
            ORDER BY request.id
            """)
    List<PlaceInformationReverificationRequest> findExpiredForUpdate(
            @Param("status") PlaceInformationReverificationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    boolean existsByPlace_IdAndStatusIn(Long placeId, Collection<PlaceInformationReverificationStatus> statuses);
}
