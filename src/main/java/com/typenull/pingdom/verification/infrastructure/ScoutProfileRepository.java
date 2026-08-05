package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.ScoutProfile;
import com.typenull.pingdom.verification.domain.ScoutProfileStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoutProfileRepository extends JpaRepository<ScoutProfile, Long> {

    Page<ScoutProfile> findAllByStatus(ScoutProfileStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT profile FROM ScoutProfile profile WHERE profile.userId = :userId")
    Optional<ScoutProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}
