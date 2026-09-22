package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 작성자·장소·유형·상태별 중복 검사와 제보 목록 및 심사용 잠금 조회를 제공. */
public interface VisitorVerificationReportRepository extends JpaRepository<VisitorVerificationReport, Long> {
    boolean existsByReporterUserIdAndPlaceIdAndReportTypeAndStatus(Long reporterUserId, Long placeId,
            VisitorVerificationReportType reportType, VisitorVerificationReportStatus status);

    /** 정정 적용 대상 자신을 제외해 동일 조건의 다른 미심사 제보가 있는지 확인. */
    boolean existsByReporterUserIdAndPlaceIdAndReportTypeAndStatusAndIdNot(
            Long reporterUserId,
            Long placeId,
            VisitorVerificationReportType reportType,
            VisitorVerificationReportStatus status,
            Long excludedId
    );

    Page<VisitorVerificationReport> findAllByReporterUserId(Long reporterUserId, Pageable pageable);

    Page<VisitorVerificationReport> findAllByStatus(VisitorVerificationReportStatus status, Pageable pageable);

    /** 심사/정정 처리에서 원본 제보의 동시 상태 변경을 직렬화하는 쓰기 잠금 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT report FROM VisitorVerificationReport report WHERE report.id = :id")
    Optional<VisitorVerificationReport> findByIdForUpdate(@Param("id") Long id);
}
