package com.typenull.pingdom.boost.infrastructure;

import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VerifiedBoostProductRepository extends JpaRepository<VerifiedBoostProduct, Long> {

    Page<VerifiedBoostProduct> findAllByMerchantOwnerUserId(Long merchantOwnerUserId, Pageable pageable);

    Optional<VerifiedBoostProduct> findByIdAndMerchantOwnerUserId(Long id, Long merchantOwnerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from VerifiedBoostProduct product where product.id = :id and product.merchantOwnerUserId = :ownerId")
    Optional<VerifiedBoostProduct> findOwnedByIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
