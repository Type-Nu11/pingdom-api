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

/** 사용자 ID를 키로 프로필을 조회하며 심사/수정용 조회에는 쓰기 잠금을 적용. */
public interface ScoutProfileRepository extends JpaRepository<ScoutProfile, Long> {

    Page<ScoutProfile> findAllByStatus(ScoutProfileStatus status, Pageable pageable);

    /** 호출 트랜잭션 동안 대상 행에 쓰기 잠금을 잡아 상태 변경을 직렬화. 부재 행 생성에 대한 잠금은 적용 범위 외. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT profile FROM ScoutProfile profile WHERE profile.userId = :userId")
    Optional<ScoutProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}
