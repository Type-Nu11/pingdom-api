package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateCandidate;
import com.typenull.pingdom.moderation.domain.place.PlaceDuplicateDecisionStatus;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceDuplicateCandidateRepository extends JpaRepository<PlaceDuplicateCandidate, Long> {

    Optional<PlaceDuplicateCandidate> findByLeftPlaceIdAndRightPlaceId(Long leftPlaceId, Long rightPlaceId);

    List<PlaceDuplicateCandidate> findByStatusOrderByDetectedAtDescIdDesc(PlaceDuplicateDecisionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT candidate FROM PlaceDuplicateCandidate candidate WHERE candidate.id = :candidateId")
    Optional<PlaceDuplicateCandidate> findByIdForUpdate(@Param("candidateId") Long candidateId);
}
