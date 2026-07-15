package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantVerificationRepository extends JpaRepository<MerchantVerification, Long> {

    boolean existsByUserIdAndIdentityStatusAndBusinessStatus(
            Long userId,
            MerchantVerificationStatus identityStatus,
            MerchantVerificationStatus businessStatus
    );

    Page<MerchantVerification> findAllByIdentityStatus(
            MerchantVerificationStatus identityStatus,
            Pageable pageable
    );

    Page<MerchantVerification> findAllByBusinessStatus(
            MerchantVerificationStatus businessStatus,
            Pageable pageable
    );

    Page<MerchantVerification> findAllByIdentityStatusAndBusinessStatus(
            MerchantVerificationStatus identityStatus,
            MerchantVerificationStatus businessStatus,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT verification FROM MerchantVerification verification WHERE verification.userId = :userId")
    Optional<MerchantVerification> findByUserIdForUpdate(@Param("userId") Long userId);
}
