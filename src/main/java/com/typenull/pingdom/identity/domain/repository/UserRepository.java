package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    // 이메일 기준 사용자 조회 메서드
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndEmailVerificationCode(String email, String emailVerificationCode);

    List<User> findAllByBannedTrueOrderByBannedAtDescIdDesc();
}
