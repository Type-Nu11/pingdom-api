package com.typenull.pingdom.verification.infrastructure;

import com.typenull.pingdom.verification.domain.ScoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoutProfileRepository extends JpaRepository<ScoutProfile, Long> {
}
