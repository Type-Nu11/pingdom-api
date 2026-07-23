package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateCandidate;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceDuplicateCandidateRepository extends JpaRepository<PlaceDuplicateCandidate, Long> {

    Optional<PlaceDuplicateCandidate> findByLeftPlaceIdAndRightPlaceId(Long leftPlaceId, Long rightPlaceId);

    List<PlaceDuplicateCandidate> findByStatusOrderByDetectedAtDescIdDesc(PlaceDuplicateDecisionStatus status);
}
