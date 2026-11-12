package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.appeal.ReportAppeal;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportAppealRepository extends JpaRepository<ReportAppeal, Long> {

    interface AppealReference {
        Long getReportId();

        Long getPostId();

        Long getTargetUserId();
    }

    // 원본 신고를 먼저 잠그기 위해 엔티티를 적재하지 않고 불변 연결 ID만 읽습니다.
    @Query("""
            SELECT appeal.reportId AS reportId, appeal.postId AS postId, appeal.targetUserId AS targetUserId
            FROM ReportAppeal appeal WHERE appeal.id = :appealId
            """)
    Optional<AppealReference> findReferenceById(@Param("appealId") Long appealId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT appeal FROM ReportAppeal appeal WHERE appeal.id = :appealId")
    Optional<ReportAppeal> findByIdForUpdate(@Param("appealId") Long appealId);

    boolean existsByReportIdAndAppellantUserIdAndStatus(
            Long reportId,
            Long appellantUserId,
            ReportAppealStatus status
    );

    Page<ReportAppeal> findAllByStatus(ReportAppealStatus status, Pageable pageable);
}
