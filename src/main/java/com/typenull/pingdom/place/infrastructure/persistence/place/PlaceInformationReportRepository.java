package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReport;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PlaceInformationReportRepository extends JpaRepository<PlaceInformationReport, Long> {

    List<PlaceInformationReport> findAllByPlace_IdOrderByCreatedAtDescIdDesc(Long placeId);

    List<PlaceInformationReport> findAllByReporterUserIdOrderByCreatedAtDescIdDesc(Long reporterUserId);

    boolean existsByReporterUserIdAndPlace_IdAndTargetTypeAndStatus(
            Long reporterUserId,
            Long placeId,
            PlaceInformationReportTargetType targetType,
            PlaceInformationReportStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlaceInformationReport> findWithLockById(Long id);
}
