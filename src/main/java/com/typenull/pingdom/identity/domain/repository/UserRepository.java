package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * 회원 식별 조회, 세션·계정 변경용 행 잠금, 정지·탈퇴 정리 대상 쿼리를 제공.
 * 정리 대상은 만료·탈퇴 시각과 ID 오름차순으로 조회하며 Pageable로 처리량을 제한.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByProfileImageUrl(String profileImageUrl);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    // PostgreSQL의 FK KEY SHARE도 차단해 참조 검사와 실제 DELETE 사이의 새 종속 행 삽입을 막는다.
    @Query(value = "SELECT * FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Optional<User> findByIdForPurge(@Param("userId") Long userId);

    // 미시도 사용자를 먼저, 이후 오래된 시도부터 조회해 보류 집단이 매 실행의 상한을 독점하지 못하게 한다.
    @Query("""
            SELECT u.id AS userId, u.withdrawnAt AS withdrawnAt,
                   COALESCE(retry.updatedAt, :neverAttemptedAt) AS attemptedAt
            FROM User u LEFT JOIN UserPurgeRetry retry ON retry.userId = u.id
            WHERE u.status = :status
              AND u.withdrawnAt IS NOT NULL
              AND u.withdrawnAt <= :cutoff
              AND (retry.userId IS NULL OR retry.nextAttemptAt <= :now)
              AND (
                  COALESCE(retry.updatedAt, :neverAttemptedAt) > :afterAttemptAt
                  OR (COALESCE(retry.updatedAt, :neverAttemptedAt) = :afterAttemptAt
                      AND (u.withdrawnAt > :afterAt OR (u.withdrawnAt = :afterAt AND u.id > :afterId)))
              )
            ORDER BY COALESCE(retry.updatedAt, :neverAttemptedAt) ASC, u.withdrawnAt ASC, u.id ASC
            """)
    List<PurgeCandidate> findPurgeCandidates(
            @Param("status") UserStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("neverAttemptedAt") LocalDateTime neverAttemptedAt,
            @Param("afterAttemptAt") LocalDateTime afterAttemptAt,
            @Param("afterAt") LocalDateTime afterAt,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    interface PurgeCandidate {
        Long getUserId();
        LocalDateTime getWithdrawnAt();
        LocalDateTime getAttemptedAt();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    // 이메일 기준 사용자 조회 메서드
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmailAndEmailVerificationCode(String email, String emailVerificationCode);

    @Query("""
            SELECT u
            FROM User u
            WHERE u.role = :role
              AND (
                    :keyword IS NULL
                    OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
            """)
    Page<User> findAllRoleAssignmentTargets(
            @Param("role") UserRole role,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT u
            FROM User u
            WHERE u.banned = true
              AND (
                    :keyword IS NULL
                    OR (:numericKeyword = true AND CAST(u.id AS string) = :keyword)
                    OR (:numericKeyword = false AND LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
                  )
              AND (:banType IS NULL OR u.banType = :banType)
              AND (:hasBannedFrom = false OR u.bannedAt >= :bannedFrom)
              AND (:hasBannedTo = false OR u.bannedAt <= :bannedTo)
              AND (
                    u.banType IS NULL
                    OR u.banType <> :temporaryType
                    OR u.banExpiresAt IS NULL
                    OR u.banExpiresAt > :now
                  )
            """)
    Page<User> findAllCurrentlyBanned(
            @Param("temporaryType") UserBanType temporaryType,
            @Param("now") LocalDateTime now,
            @Param("keyword") String keyword,
            @Param("numericKeyword") boolean numericKeyword,
            @Param("banType") UserBanType banType,
            @Param("hasBannedFrom") boolean hasBannedFrom,
            @Param("bannedFrom") LocalDateTime bannedFrom,
            @Param("hasBannedTo") boolean hasBannedTo,
            @Param("bannedTo") LocalDateTime bannedTo,
            Pageable pageable
    );

    @Query("""
            SELECT new com.typenull.pingdom.identity.domain.repository.CurrentBannedUserCounts(
                COUNT(u),
                SUM(CASE WHEN u.banType = :permanentType OR u.banType IS NULL THEN 1 ELSE 0 END),
                SUM(CASE WHEN u.banType = :temporaryType THEN 1 ELSE 0 END)
            )
            FROM User u
            WHERE u.banned = true
              AND (
                    :keyword IS NULL
                    OR CAST(u.id AS string) = :keyword
                    OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  )
              AND (
                    u.banType IS NULL
                    OR u.banType <> :temporaryType
                    OR u.banExpiresAt IS NULL
                    OR u.banExpiresAt > :now
                  )
            """)
    CurrentBannedUserCounts countCurrentlyBannedByType(
            @Param("permanentType") UserBanType permanentType,
            @Param("temporaryType") UserBanType temporaryType,
            @Param("now") LocalDateTime now,
            @Param("keyword") String keyword
    );

    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.banned = true
              AND u.banType = :temporaryType
              AND u.banExpiresAt IS NOT NULL
              AND u.banExpiresAt > :now
              AND u.banExpiresAt <= :expiresUntil
            """)
    long countTemporaryBansExpiringUntil(
            @Param("temporaryType") UserBanType temporaryType,
            @Param("now") LocalDateTime now,
            @Param("expiresUntil") LocalDateTime expiresUntil
    );

    @Query("""
            SELECT u
            FROM User u
            WHERE u.banned = true
              AND u.banType = :temporaryType
              AND u.banExpiresAt IS NOT NULL
              AND u.banExpiresAt <= :now
            ORDER BY u.banExpiresAt ASC, u.id ASC
            """)
    List<User> findExpiredTemporaryBannedUsers(
            @Param("temporaryType") UserBanType temporaryType,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            SELECT u.id
            FROM User u
            WHERE u.status = :status
              AND u.withdrawnAt IS NOT NULL
              AND u.withdrawnAt <= :cutoff
            ORDER BY u.withdrawnAt ASC, u.id ASC
            """)
    List<Long> findExpiredWithdrawnUserIds(
            @Param("status") UserStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Query("""
            SELECT u.id
            FROM User u
            WHERE u.status = :status
              AND u.withdrawnAt IS NOT NULL
              AND u.withdrawnAt <= :cutoff
              AND (
                    EXISTS (
                        SELECT 1
                        FROM TravelSchedule schedule
                        WHERE schedule.user.id = u.id
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM UserCurrentActivityIntent intent
                        WHERE intent.user.id = u.id
                    )
                  )
            ORDER BY u.withdrawnAt ASC, u.id ASC
            """)
    List<Long> findExpiredWithdrawnUserIdsWithTravelData(
            @Param("status") UserStatus status,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );
}
