package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.VisitVerificationSession;
import com.typenull.pingdom.verification.domain.VisitVerificationSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 사용자·장소·인증 날짜별 세션 복구, 잠금 조회 및 보관 기한 정리를 제공. */
public interface VisitVerificationSessionRepository extends JpaRepository<VisitVerificationSession, Long> {
    /** 지정 상태 집합에 속한 당일 동일 장소 세션 중 ID가 가장 큰 하나를 반환. */
    Optional<VisitVerificationSession> findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
            Long touristUserId, Long placeId, LocalDate verificationDate, Collection<VisitVerificationSessionStatus> statuses);

    /** foreground 장소 탐색 전 당일 지정 상태의 세션을 마지막 서버 확인 시각 역순으로 반환. */
    List<VisitVerificationSession> findAllByTouristUserIdAndVerificationDateAndStatusInOrderByLastVerifiedAtDesc(
            Long touristUserId,
            LocalDate verificationDate,
            Collection<VisitVerificationSessionStatus> statuses);

    /** 소유자 조건으로 세션을 조회하면서 쓰기 잠금을 설정. 트랜잭션 안에서 관측/만료 갱신에 사용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from VisitVerificationSession session where session.id = :id and session.touristUserId = :userId")
    Optional<VisitVerificationSession> findByIdAndTouristUserIdForUpdate(@Param("id") Long id,
            @Param("userId") Long userId);

    /** 상태와 무관하게 세션 만료 시각이 기준 이하인 행을 삭제하고 삭제 건수를 반환. */
    long deleteByExpiresAtLessThanEqual(Instant expiresAt);
}
