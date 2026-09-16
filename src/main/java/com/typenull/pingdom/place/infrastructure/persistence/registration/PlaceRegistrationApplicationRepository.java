package com.typenull.pingdom.place.infrastructure.persistence.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationApplication;
import com.typenull.pingdom.place.domain.registration.MerchantPlaceApplicationType;
import com.typenull.pingdom.place.domain.registration.PlaceRegistrationStatus;
import java.time.LocalDateTime;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRegistrationApplicationRepository extends JpaRepository<PlaceRegistrationApplication, Long> {

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

    @Query("""
            SELECT application
            FROM PlaceRegistrationApplication application
            LEFT JOIN User applicant ON applicant.id = application.applicantUserId
            LEFT JOIN MapPlace existingPlace ON existingPlace.id = application.existingPlaceId
            WHERE application.status IN :statuses
              AND (:applicationType IS NULL OR application.applicationType = :applicationType)
              AND (
                    :keyword IS NULL
                    OR LOWER(application.placeName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(applicant.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(existingPlace.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
              AND (:submittedFrom IS NULL OR application.submittedAt >= :submittedFrom)
              AND (:submittedTo IS NULL OR application.submittedAt <= :submittedTo)
            """)
    Page<PlaceRegistrationApplication> searchForAdmin(
            @Param("statuses") Collection<PlaceRegistrationStatus> statuses,
            @Param("applicationType") MerchantPlaceApplicationType applicationType,
            @Param("keyword") String keyword,
            @Param("submittedFrom") LocalDateTime submittedFrom,
            @Param("submittedTo") LocalDateTime submittedTo,
            Pageable pageable
    );
    long countByStatus(PlaceRegistrationStatus status);
    Optional<PlaceRegistrationApplication> findByIdAndApplicantUserId(Long id, Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlaceRegistrationApplication a where a.id = :id")
    Optional<PlaceRegistrationApplication> findByIdForUpdate(@Param("id") Long id);
}
