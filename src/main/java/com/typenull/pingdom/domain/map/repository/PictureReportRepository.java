package com.typenull.pingdom.domain.map.repository;

import com.typenull.pingdom.domain.map.domain.PictureReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PictureReportRepository extends JpaRepository<PictureReport, Long> {

    boolean existsByReporterUserIdAndMapImage_Id(Long reporterUserId, Long mapImageId);

    @EntityGraph(attributePaths = "mapImage")
    Page<PictureReport> findAllBy(Pageable pageable);
}
