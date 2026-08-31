package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceMediaUpload;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantPlaceMediaUploadRepository extends JpaRepository<MerchantPlaceMediaUpload, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT upload FROM MerchantPlaceMediaUpload upload WHERE upload.s3Key = :s3Key")
    Optional<MerchantPlaceMediaUpload> findByS3KeyForUpdate(@Param("s3Key") String s3Key);
}
