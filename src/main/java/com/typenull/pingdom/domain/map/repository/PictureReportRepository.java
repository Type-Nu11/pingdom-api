package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.PictureReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PictureReportRepository extends JpaRepository<PictureReport, Long> {

    boolean existsByReporterUserIdAndMapImage_Id(Long reporterUserId, Long mapImageId);
}
