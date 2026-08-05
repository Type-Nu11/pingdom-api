package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPlaceInformationRepository extends JpaRepository<MerchantPlaceInformation, Long> {

    Optional<MerchantPlaceInformation> findByPlaceId(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT information FROM MerchantPlaceInformation information WHERE information.placeId = :placeId")
    Optional<MerchantPlaceInformation> findByPlaceIdForUpdate(@Param("placeId") Long placeId);
}
