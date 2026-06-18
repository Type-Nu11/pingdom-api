package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.ad.AdminAd;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAdRepository extends JpaRepository<AdminAd, Long> {
}
