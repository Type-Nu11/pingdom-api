package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRegistrationApplicationRepository extends JpaRepository<PlaceRegistrationApplication, Long> {
    Page<PlaceRegistrationApplication> findAllByApplicantUserId(Long userId, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByApplicantUserIdAndApplicationType(
            Long userId, MerchantPlaceApplicationType applicationType, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByApplicationType(
            MerchantPlaceApplicationType applicationType, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByApplicantUserIdAndApplicationTypeNot(
            Long userId, MerchantPlaceApplicationType applicationType, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByApplicationTypeNot(
            MerchantPlaceApplicationType applicationType, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByApplicationTypeNotAndStatus(
            MerchantPlaceApplicationType applicationType, PlaceRegistrationStatus status, Pageable pageable);
    long countByApplicationTypeNotAndStatus(
            MerchantPlaceApplicationType applicationType, PlaceRegistrationStatus status);
    boolean existsByApplicantUserIdAndApplicationTypeNotAndStatus(
            Long applicantUserId, MerchantPlaceApplicationType applicationType, PlaceRegistrationStatus status);
    boolean existsByExistingPlaceIdAndApplicationTypeAndStatus(
            Long existingPlaceId, MerchantPlaceApplicationType applicationType, PlaceRegistrationStatus status);
    Page<PlaceRegistrationApplication> findAllByStatus(PlaceRegistrationStatus status, Pageable pageable);
    Optional<PlaceRegistrationApplication> findByIdAndApplicantUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlaceRegistrationApplication a where a.id = :id")
    Optional<PlaceRegistrationApplication> findByIdForUpdate(@Param("id") Long id);
}
