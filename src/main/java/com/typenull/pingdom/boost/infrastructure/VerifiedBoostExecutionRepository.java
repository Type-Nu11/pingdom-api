package com.typenull.pingdom.boost.infrastructure;

import com.typenull.pingdom.boost.domain.VerifiedBoostExecution;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface VerifiedBoostExecutionRepository extends JpaRepository<VerifiedBoostExecution, Long> {

    Optional<VerifiedBoostExecution> findBySelectionId(Long selectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select execution
            from VerifiedBoostExecution execution
            where execution.id = :executionId
              and execution.merchantOwnerUserId = :ownerId
            """)
    Optional<VerifiedBoostExecution> findOwnedByIdForUpdate(@Param("executionId") Long executionId,
            @Param("ownerId") Long ownerId);

    Page<VerifiedBoostExecution> findAllByMerchantOwnerUserId(Long ownerId, Pageable pageable);

    @Query("""
            select execution
            from VerifiedBoostExecution execution
            where execution.placeId = :placeId
              and execution.status = com.typenull.pingdom.boost.domain.VerifiedBoostExecutionStatus.ACTIVE
              and execution.startedAt <= :now
              and execution.endsAt > :now
            """)
    Optional<VerifiedBoostExecution> findActiveByPlaceId(@Param("placeId") Long placeId,
            @Param("now") LocalDateTime now);

    @Query("""
            select execution.placeId
            from VerifiedBoostExecution execution, MerchantOwnerPlace ownerPlace
            where execution.placeId in :placeIds
              and execution.placeId = ownerPlace.placeId
              and execution.merchantOwnerUserId = ownerPlace.merchantOwnerUserId
              and execution.status = com.typenull.pingdom.boost.domain.VerifiedBoostExecutionStatus.ACTIVE
              and execution.startedAt <= :now
              and execution.endsAt > :now
              and ownerPlace.operationalQualityStatus = com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus.HEALTHY
            """)
    List<Long> findEligibleActivePlaceIds(@Param("placeIds") Collection<Long> placeIds,
            @Param("now") LocalDateTime now);
}
