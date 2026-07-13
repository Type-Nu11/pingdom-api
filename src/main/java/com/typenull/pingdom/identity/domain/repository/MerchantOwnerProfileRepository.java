package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantOwnerProfileRepository extends JpaRepository<MerchantOwnerProfile, Long> {

    boolean existsByUserIdAndStatus(Long userId, MerchantOwnerStatus status);

    Page<MerchantOwnerProfile> findAllByStatus(MerchantOwnerStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT profile FROM MerchantOwnerProfile profile WHERE profile.userId = :userId")
    Optional<MerchantOwnerProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}
