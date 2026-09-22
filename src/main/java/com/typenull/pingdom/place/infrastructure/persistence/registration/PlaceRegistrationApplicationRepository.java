package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

/**
 * 신청자·상태·유형별 신청 조회와 심사 대상 행 잠금을 제공.
 * Pageable 조회의 정렬은 호출자가 지정하며 findByIdForUpdate는 호출 트랜잭션에서 신청 행에 쓰기 잠금을 적용.
 */
public interface PlaceRegistrationApplicationRepository extends JpaRepository<PlaceRegistrationApplication, Long>,
        JpaSpecificationExecutor<PlaceRegistrationApplication> {

    Page<PlaceRegistrationApplication> findByStatusAndCompletedPlaceIdIsNotNull(
            PlaceRegistrationStatus status,
            Pageable pageable
    );
    Page<PlaceRegistrationApplication> findAllByApplicantUserId(Long userId, Pageable pageable);
    boolean existsByApplicantUserIdAndStatus(
            Long applicantUserId,
            PlaceRegistrationStatus status
    );
    boolean existsByExistingPlaceIdAndApplicationTypeAndStatus(
            Long existingPlaceId, MerchantPlaceApplicationType applicationType, PlaceRegistrationStatus status);
    Page<PlaceRegistrationApplication> findAllByStatus(PlaceRegistrationStatus status, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByStatusIn(
            Collection<PlaceRegistrationStatus> statuses,
            Pageable pageable
    );
    Page<PlaceRegistrationApplication> findAllByApplicationType(MerchantPlaceApplicationType applicationType, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByStatusAndApplicationType(
            PlaceRegistrationStatus status, MerchantPlaceApplicationType applicationType, Pageable pageable);
    Page<PlaceRegistrationApplication> findAllByStatusInAndApplicationType(
            Collection<PlaceRegistrationStatus> statuses,
            MerchantPlaceApplicationType applicationType,
            Pageable pageable
    );

    long countByStatus(PlaceRegistrationStatus status);
    Optional<PlaceRegistrationApplication> findByIdAndApplicantUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlaceRegistrationApplication a where a.id = :id")
    Optional<PlaceRegistrationApplication> findByIdForUpdate(@Param("id") Long id);
}
