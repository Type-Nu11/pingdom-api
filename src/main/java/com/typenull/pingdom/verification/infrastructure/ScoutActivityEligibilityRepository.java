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

public interface ScoutActivityEligibilityRepository extends JpaRepository<ScoutActivityEligibility, Long> {

    Optional<ScoutActivityEligibility> findByScoutUserIdAndStatus(
            Long scoutUserId,
            ScoutActivityEligibilityStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT eligibility FROM ScoutActivityEligibility eligibility WHERE eligibility.scoutUserId = :scoutUserId")
    Optional<ScoutActivityEligibility> findByScoutUserIdForUpdate(@Param("scoutUserId") Long scoutUserId);

    boolean existsByScoutUserIdAndStatusAndEligibleFromLessThanEqualAndEligibleUntilIsNull(
            Long scoutUserId,
            ScoutActivityEligibilityStatus status,
            LocalDateTime now
    );
}
