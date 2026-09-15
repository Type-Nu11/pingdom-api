package com.typenull.pingdom.community.infrastructure.persistence;

import com.typenull.pingdom.community.domain.CommunityReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {

    boolean existsByReporterUserIdAndPost_Id(Long reporterUserId, Long postId);

    boolean existsByReporterUserIdAndComment_Id(Long reporterUserId, Long commentId);
}
