package com.typenull.pingdom.boost.infrastructure;

import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VerifiedBoostProductRepository extends JpaRepository<VerifiedBoostProduct, Long> {

    Page<VerifiedBoostProduct> findAllByStatus(VerifiedBoostProductStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from VerifiedBoostProduct product where product.id = :id")
    Optional<VerifiedBoostProduct> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select product from VerifiedBoostProduct product where product.id = :id and product.status = com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus.ACTIVE")
    Optional<VerifiedBoostProduct> findActiveByIdForShare(@Param("id") Long id);
}
