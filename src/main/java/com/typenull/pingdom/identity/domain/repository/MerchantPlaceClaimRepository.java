package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaim;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceClaimStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPlaceClaimRepository extends JpaRepository<MerchantPlaceClaim, Long> {

    boolean existsByPlaceIdAndStatus(Long placeId, MerchantPlaceClaimStatus status);

    Optional<MerchantPlaceClaim> findByIdAndMerchantOwnerUserId(Long id, Long merchantOwnerUserId);

    Page<MerchantPlaceClaim> findAllByMerchantOwnerUserId(Long merchantOwnerUserId, Pageable pageable);

    List<MerchantPlaceClaim> findAllByMerchantOwnerUserIdOrderByCreatedAtDescIdDesc(Long merchantOwnerUserId);

    Page<MerchantPlaceClaim> findAllByStatus(MerchantPlaceClaimStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT claim FROM MerchantPlaceClaim claim WHERE claim.id = :claimId")
    Optional<MerchantPlaceClaim> findByIdForUpdate(@Param("claimId") Long claimId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM MerchantPlaceClaim claim WHERE claim.merchantOwnerUserId = :userId")
    int deleteAllByMerchantOwnerUserId(@Param("userId") Long userId);
}
