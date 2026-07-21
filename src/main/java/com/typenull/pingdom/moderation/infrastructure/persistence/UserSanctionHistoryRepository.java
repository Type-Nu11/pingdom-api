package com.typenull.pingdom.moderation.infrastructure.persistence;

import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserSanctionHistoryRepository extends
        JpaRepository<UserSanctionHistory, Long>,
        JpaSpecificationExecutor<UserSanctionHistory> {

    List<UserSanctionHistory> findByActionIn(Collection<UserSanctionAction> actions, Pageable pageable);
}
