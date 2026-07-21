package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoutFieldReportRepository extends JpaRepository<ScoutFieldReport, Long> {

    boolean existsByScoutUserIdAndPlaceIdAndReportTypeAndStatus(
            Long scoutUserId,
            Long placeId,
            ScoutFieldReportType reportType,
            ScoutFieldReportStatus status
    );

    Page<ScoutFieldReport> findAllByScoutUserId(Long scoutUserId, Pageable pageable);

    Page<ScoutFieldReport> findAllByStatus(ScoutFieldReportStatus status, Pageable pageable);

    boolean existsByPlaceId(Long placeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT report FROM ScoutFieldReport report WHERE report.id = :id")
    Optional<ScoutFieldReport> findByIdForUpdate(@Param("id") Long id);
}
