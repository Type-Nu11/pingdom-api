package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
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

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "1000"))
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

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
            WHERE u.banned = true
              AND (
                    :keyword IS NULL
                    OR (:numericKeyword = true AND CAST(u.id AS string) = :keyword)
                    OR (:numericKeyword = false AND LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))
                  )
              AND (:banType IS NULL OR u.banType = :banType)
              AND (:bannedFrom IS NULL OR u.bannedAt >= :bannedFrom)
              AND (:bannedTo IS NULL OR u.bannedAt <= :bannedTo)
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
            @Param("bannedFrom") LocalDateTime bannedFrom,
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
