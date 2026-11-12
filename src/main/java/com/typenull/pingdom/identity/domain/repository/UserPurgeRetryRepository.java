package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.UserPurgeRetry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPurgeRetryRepository extends JpaRepository<UserPurgeRetry, Long> {
}
