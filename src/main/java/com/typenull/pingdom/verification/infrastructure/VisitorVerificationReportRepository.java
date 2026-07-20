package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VisitorVerificationReportRepository extends JpaRepository<VisitorVerificationReport, Long> {
    boolean existsByReporterUserIdAndPlaceIdAndReportTypeAndStatus(Long reporterUserId, Long placeId,
            VisitorVerificationReportType reportType, VisitorVerificationReportStatus status);

    Page<VisitorVerificationReport> findAllByReporterUserId(Long reporterUserId, Pageable pageable);

    Page<VisitorVerificationReport> findAllByStatus(VisitorVerificationReportStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT report FROM VisitorVerificationReport report WHERE report.id = :id")
    Optional<VisitorVerificationReport> findByIdForUpdate(@Param("id") Long id);
}
