package com.typenull.pingdom.place.infrastructure.persistence.place;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportDispute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PlaceInformationReportDisputeRepository extends JpaRepository<PlaceInformationReportDispute, Long> {

    List<PlaceInformationReportDispute> findAllByReport_IdOrderByCreatedAtDescIdDesc(Long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT dispute FROM PlaceInformationReportDispute dispute
            WHERE dispute.id = :id AND dispute.report.id = :reportId
            """)
    Optional<PlaceInformationReportDispute> findByIdAndReport_IdForUpdate(
            @Param("id") Long id,
            @Param("reportId") Long reportId
    );
}
