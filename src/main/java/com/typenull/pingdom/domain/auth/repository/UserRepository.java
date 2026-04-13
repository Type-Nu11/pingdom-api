package com.typenull.pingdom.domain.auth.repository;

import java.util.Optional;

import com.typenull.pingdom.domain.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);
}
