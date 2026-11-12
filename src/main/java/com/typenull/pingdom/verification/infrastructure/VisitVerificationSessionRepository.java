package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.verification.domain.VisitVerificationSession;
import com.typenull.pingdom.verification.domain.VisitVerificationSessionStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 사용자·장소·인증 날짜별 세션 복구, 잠금 조회 및 보관 기한 정리를 제공. */
public interface VisitVerificationSessionRepository extends JpaRepository<VisitVerificationSession, Long> {
    /** 지정 상태 집합에 속한 당일 동일 장소 세션 중 ID가 가장 큰 하나를 반환. */
    Optional<VisitVerificationSession> findFirstByTouristUserIdAndPlaceIdAndVerificationDateAndStatusInOrderByIdDesc(
            Long touristUserId, Long placeId, LocalDate verificationDate, Collection<VisitVerificationSessionStatus> statuses);

    /** scalar projection으로 읽어 만료 UPDATE가 원본 커서와 1차 캐시를 변경하지 않게 합니다. */
    @Query("""
            SELECT session.id AS id, session.placeId AS placeId, session.status AS status,
                   session.expiresAt AS expiresAt, session.lastVerifiedAt AS lastVerifiedAt,
                   session.requiredRadiusMeters AS requiredRadiusMeters,
                   place.operatingStatus AS operatingStatus, place.discoveryStatus AS discoveryStatus,
                   place.latitude AS latitude, place.longitude AS longitude
            FROM VisitVerificationSession session
            LEFT JOIN MapPlace place ON place.id = session.placeId
            WHERE session.touristUserId = :userId
              AND session.verificationDate = :verificationDate
              AND session.status IN :statuses
              AND (:cursorTime IS NULL OR session.lastVerifiedAt < :cursorTime
                   OR (session.lastVerifiedAt = :cursorTime AND session.id < :cursorId))
            ORDER BY session.lastVerifiedAt DESC, session.id DESC
            """)
    List<ForegroundRecoveryCandidate> findForegroundRecoveryBatch(
            @Param("userId") Long userId,
            @Param("verificationDate") LocalDate verificationDate,
            @Param("statuses") Collection<VisitVerificationSessionStatus> statuses,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 실제 검사한 만료 prefix만 갱신하며 완료/관측과의 충돌은 호출 트랜잭션에서 처리합니다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE VisitVerificationSession session
            SET session.status = :expiredStatus, session.lastVerifiedAt = :now
            WHERE session.id IN :ids
              AND session.touristUserId = :userId
              AND session.verificationDate = :verificationDate
              AND session.status IN :activeStatuses
              AND (session.expiresAt <= :now OR session.lastVerifiedAt < :observationCutoff)
            """)
    int expireForegroundPrefix(
            @Param("userId") Long userId,
            @Param("verificationDate") LocalDate verificationDate,
            @Param("ids") Collection<Long> ids,
            @Param("activeStatuses") Collection<VisitVerificationSessionStatus> activeStatuses,
            @Param("expiredStatus") VisitVerificationSessionStatus expiredStatus,
            @Param("now") Instant now,
            @Param("observationCutoff") Instant observationCutoff);

    interface ForegroundRecoveryCandidate {
        Long getId();
        Long getPlaceId();
        VisitVerificationSessionStatus getStatus();
        Instant getExpiresAt();
        Instant getLastVerifiedAt();
        double getRequiredRadiusMeters();
        PlaceOperatingStatus getOperatingStatus();
        PlaceDiscoveryStatus getDiscoveryStatus();
        Double getLatitude();
        Double getLongitude();
    }

    /** 소유자 조건으로 세션을 조회하면서 쓰기 잠금을 설정. 트랜잭션 안에서 관측/만료 갱신에 사용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from VisitVerificationSession session where session.id = :id and session.touristUserId = :userId")
    Optional<VisitVerificationSession> findByIdAndTouristUserIdForUpdate(@Param("id") Long id,
            @Param("userId") Long userId);

    /** 상태와 무관하게 세션 만료 시각이 기준 이하인 행을 삭제하고 삭제 건수를 반환. */
    long deleteByExpiresAtLessThanEqual(Instant expiresAt);
}
