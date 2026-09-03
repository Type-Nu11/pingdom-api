package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitVerificationSession;
import com.typenull.pingdom.verification.domain.VisitVerificationSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VisitVerificationSessionRepository extends JpaRepository<VisitVerificationSession, Long> {
    Optional<VisitVerificationSession> findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
            Long touristUserId, Long placeId, LocalDate verificationDate, Collection<VisitVerificationSessionStatus> statuses);

    // foreground 신규 장소 탐색 전에 진행 중인 세션을 최근 관측순으로 복구합니다.
    List<VisitVerificationSession> findAllByTouristUserIdAndVerificationDateAndStatusInOrderByLastVerifiedAtDesc(
            Long touristUserId,
            LocalDate verificationDate,
            Collection<VisitVerificationSessionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from VisitVerificationSession session where session.id = :id and session.touristUserId = :userId")
    Optional<VisitVerificationSession> findByIdAndTouristUserIdForUpdate(@Param("id") Long id,
            @Param("userId") Long userId);

    long deleteByExpiresAtLessThanEqual(Instant expiresAt);
}
