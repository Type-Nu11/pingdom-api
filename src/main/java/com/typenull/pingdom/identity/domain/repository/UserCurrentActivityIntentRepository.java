package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.travel.UserCurrentActivityIntent;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCurrentActivityIntentRepository extends JpaRepository<UserCurrentActivityIntent, Long> {

    Optional<UserCurrentActivityIntent> findByUser_Id(Long userId);

    @Modifying
    @Query("DELETE FROM UserCurrentActivityIntent intent WHERE intent.expiresAt <= :cutoff")
    int deleteExpiredAtOrBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM UserCurrentActivityIntent intent WHERE intent.user.id IN :userIds")
    int deleteAllByUserIds(@Param("userIds") Collection<Long> userIds);
}
