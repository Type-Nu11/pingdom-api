package com.typenull.pingdom.identity.domain.repository;

import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleAssignmentRepository extends JpaRepository<AdminRoleAssignment, Long> {

    List<AdminRoleAssignment> findAllByAdminUserIdAndStatus(
            Long adminUserId,
            AdminRoleAssignmentStatus status
    );

    Optional<AdminRoleAssignment> findByAdminUserIdAndRoleAndStatus(
            Long adminUserId,
            AdminRole role,
            AdminRoleAssignmentStatus status
    );
}
