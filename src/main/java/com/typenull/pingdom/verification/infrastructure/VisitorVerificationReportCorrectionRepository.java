package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrection;
import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 정정 이력과 상태별 페이지를 제공한다. 목록은 report EntityGraph로 원본 정보를 함께 로드해 응답 매핑에 사용한다. */
public interface VisitorVerificationReportCorrectionRepository
        extends JpaRepository<VisitorVerificationReportCorrection, Long> {

    boolean existsByReport_IdAndStatus(Long reportId, VisitorVerificationReportCorrectionStatus status);

    @Override
    @EntityGraph(attributePaths = "report")
    Page<VisitorVerificationReportCorrection> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "report")
    Page<VisitorVerificationReportCorrection> findAllByReport_IdAndRequesterUserId(
            Long reportId,
            Long requesterUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "report")
    Page<VisitorVerificationReportCorrection> findAllByStatus(
            VisitorVerificationReportCorrectionStatus status,
            Pageable pageable
    );

    /** 정정 심사 중 같은 정정이 중복 처리되지 않도록 쓰기 잠금 조회한다. 원본 제보 잠금은 별도다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT correction FROM VisitorVerificationReportCorrection correction WHERE correction.id = :id")
    Optional<VisitorVerificationReportCorrection> findByIdForUpdate(@Param("id") Long id);
}
