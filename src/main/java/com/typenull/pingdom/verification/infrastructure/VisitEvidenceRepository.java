package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitEvidence;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 체크인 증빙의 존재·소유자 조회와 만료 시각 기준 정리 대상을 제공한다. */
public interface VisitEvidenceRepository extends JpaRepository<VisitEvidence, Long> {
    boolean existsByLocationCheckInId(Long locationCheckInId);
    /** 체크인과 사용자 두 조건으로 제한한다. 만료 시각 필터는 이 조회에 포함되지 않는다. */
    Optional<VisitEvidence> findByLocationCheckInIdAndTouristUserId(Long locationCheckInId, Long touristUserId);
    /** 기준 시각과 같거나 먼저 만료된 증빙을 만료 시각·ID 오름차순으로 제한 조회한다. */
    List<VisitEvidence> findAllByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(Instant expiresAt, Pageable pageable);
}
