package com.typenull.pingdom.campaign.infrastructure;

import com.typenull.pingdom.campaign.domain.MerchantBrand;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantBrandRepository extends JpaRepository<MerchantBrand, Long> {

    Optional<MerchantBrand> findByIdAndMerchantOwnerUserId(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT brand FROM MerchantBrand brand WHERE brand.id = :id AND brand.merchantOwnerUserId = :ownerId")
    Optional<MerchantBrand> findOwnedByIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    Page<MerchantBrand> findAllByMerchantOwnerUserId(Long ownerId, Pageable pageable);

    boolean existsByMerchantOwnerUserIdAndName(Long ownerId, String name);
}
