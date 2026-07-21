package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportDispute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface PlaceInformationReportDisputeRepository extends JpaRepository<PlaceInformationReportDispute, Long> {

    List<PlaceInformationReportDispute> findAllByReport_IdOrderByCreatedAtDescIdDesc(Long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlaceInformationReportDispute> findWithLockByIdAndReport_Id(Long id, Long reportId);
}
