package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantOwnerPlaceRepository extends JpaRepository<MerchantOwnerPlace, Long> {

    List<MerchantOwnerPlace> findAllByMerchantOwnerUserIdOrderByPlaceIdAsc(Long merchantOwnerUserId);

    List<MerchantOwnerPlace> findAllByPlaceIdIn(Collection<Long> placeIds);

    @Modifying
    @Query("DELETE FROM MerchantOwnerPlace place WHERE place.merchantOwnerUserId = :userId")
    int deleteAllByMerchantOwnerUserId(@Param("userId") Long userId);
}
