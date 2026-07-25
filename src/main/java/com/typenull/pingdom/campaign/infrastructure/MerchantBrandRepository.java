package com.typenull.pingdom.campaign.infrastructure;

import com.typenull.pingdom.campaign.domain.MerchantBrand;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantBrandRepository extends JpaRepository<MerchantBrand, Long> {

    Optional<MerchantBrand> findByIdAndMerchantOwnerUserId(Long id, Long ownerId);

    Page<MerchantBrand> findAllByMerchantOwnerUserId(Long ownerId, Pageable pageable);

    boolean existsByMerchantOwnerUserIdAndName(Long ownerId, String name);
}
