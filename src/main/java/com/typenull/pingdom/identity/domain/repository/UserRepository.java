package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByIdAndStatusAndBannedFalse(Long id, UserStatus status);

    Optional<User> findByUsername(String username);

    // 이메일 기준 사용자 조회 메서드
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndEmailVerificationCode(String email, String emailVerificationCode);

    Page<User> findAllByBannedTrue(Pageable pageable);

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
}
