package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.appeal.ReportAppeal;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportAppealRepository extends JpaRepository<ReportAppeal, Long> {

    boolean existsByReportIdAndAppellantUserIdAndStatus(
            Long reportId,
            Long appellantUserId,
            ReportAppealStatus status
    );

    Page<ReportAppeal> findAllByStatus(ReportAppealStatus status, Pageable pageable);
}
