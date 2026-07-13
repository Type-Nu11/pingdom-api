package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserSanctionHistoryRepository extends
        JpaRepository<UserSanctionHistory, Long>,
        JpaSpecificationExecutor<UserSanctionHistory> {
}
