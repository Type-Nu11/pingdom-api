package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityReport;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long>, JpaSpecificationExecutor<CommunityReport> {

    boolean existsByReporterUserIdAndPost_Id(Long reporterUserId, Long postId);

    boolean existsByReporterUserIdAndComment_Id(Long reporterUserId, Long commentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from CommunityReport report where report.id = :reportId")
    Optional<CommunityReport> findByIdForUpdate(@Param("reportId") Long reportId);
}
