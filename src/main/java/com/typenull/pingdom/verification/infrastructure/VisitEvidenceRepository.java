package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitEvidenceRepository extends JpaRepository<VisitEvidence, Long> {
    boolean existsByLocationCheckInId(Long locationCheckInId);
    Optional<VisitEvidence> findByLocationCheckInIdAndTouristUserId(Long locationCheckInId, Long touristUserId);
    List<VisitEvidence> findAllByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(Instant expiresAt, Pageable pageable);
}
