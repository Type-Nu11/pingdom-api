package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.ScoutActivityEligibility;
import com.typenull.pingdom.verification.domain.ScoutActivityEligibilityStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoutActivityEligibilityRepository extends JpaRepository<ScoutActivityEligibility, Long> {

    Optional<ScoutActivityEligibility> findByScoutUserIdAndStatus(
            Long scoutUserId,
            ScoutActivityEligibilityStatus status
    );

    boolean existsByScoutUserIdAndStatusAndEligibleFromLessThanEqualAndEligibleUntilIsNull(
            Long scoutUserId,
            ScoutActivityEligibilityStatus status,
            LocalDateTime now
    );
}
