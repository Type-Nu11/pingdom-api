package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitorVerificationReportCorrectionRepository
        extends JpaRepository<VisitorVerificationReportCorrection, Long> {

    boolean existsByReport_IdAndStatus(Long reportId, VisitorVerificationReportCorrectionStatus status);

    Page<VisitorVerificationReportCorrection> findAllByReport_IdAndRequesterUserId(
            Long reportId,
            Long requesterUserId,
            Pageable pageable
    );

    Page<VisitorVerificationReportCorrection> findAllByStatus(
            VisitorVerificationReportCorrectionStatus status,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT correction FROM VisitorVerificationReportCorrection correction WHERE correction.id = :id")
    Optional<VisitorVerificationReportCorrection> findByIdForUpdate(@Param("id") Long id);
}
