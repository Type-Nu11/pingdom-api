package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken token
            SET token.usedAt = :now
            WHERE token.user.id = :userId
              AND token.usedAt IS NULL
              AND token.expiresAt > :now
            """)
    int markActiveTokensUsed(
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );
}
