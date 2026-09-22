package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.ScoutFieldReport;
import com.typenull.pingdom.verification.domain.ScoutFieldReportStatus;
import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 제보 중복 확인·사용자 및 상태별 페이지·심사용 잠금 조회를 제공한다. */
public interface ScoutFieldReportRepository extends JpaRepository<ScoutFieldReport, Long> {

    /** 동일 Scout·장소·유형·상태 조합의 존재만 판정한다. 동시 제출 방어에는 별도 DB 유일 제약이 필요하다. */
    boolean existsByScoutUserIdAndPlaceIdAndReportTypeAndStatus(
            Long scoutUserId,
            Long placeId,
            ScoutFieldReportType reportType,
            ScoutFieldReportStatus status
    );

    Page<ScoutFieldReport> findAllByScoutUserId(Long scoutUserId, Pageable pageable);

    Page<ScoutFieldReport> findAllByStatus(ScoutFieldReportStatus status, Pageable pageable);

    boolean existsByPlaceId(Long placeId);

    /** 호출 트랜잭션 동안 대상 행에 쓰기 잠금을 잡아 상태 변경을 직렬화한다. 부재 행을 새로 생성하는 잠금은 아니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT report FROM ScoutFieldReport report WHERE report.id = :id")
    Optional<ScoutFieldReport> findByIdForUpdate(@Param("id") Long id);
}
