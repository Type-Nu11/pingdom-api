package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scout 활동 자격의 상태별 조회와 갱신용 잠금 조회를 제공. */
public interface ScoutActivityEligibilityRepository extends JpaRepository<ScoutActivityEligibility, Long> {

    Optional<ScoutActivityEligibility> findByScoutUserIdAndStatus(
            Long scoutUserId,
            ScoutActivityEligibilityStatus status
    );

    /** 호출 트랜잭션 동안 대상 행에 쓰기 잠금을 잡아 상태 변경을 직렬화. 부재 행 생성에 대한 잠금은 적용 범위 외. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT eligibility FROM ScoutActivityEligibility eligibility WHERE eligibility.scoutUserId = :scoutUserId")
    Optional<ScoutActivityEligibility> findByScoutUserIdForUpdate(@Param("scoutUserId") Long scoutUserId);

    /** 지정 상태이면서 시작 시각이 도래한 무기한 자격만 찾음. 유한 종료 자격은 이 query에서 제외. */
    boolean existsByScoutUserIdAndStatusAndEligibleFromLessThanEqualAndEligibleUntilIsNull(
            Long scoutUserId,
            ScoutActivityEligibilityStatus status,
            LocalDateTime now
    );
}
