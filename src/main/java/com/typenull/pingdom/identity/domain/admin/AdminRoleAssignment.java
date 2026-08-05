package com.typenull.pingdom.identity.domain.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "admin_role_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AdminRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRoleAssignmentStatus status;

    @Column(name = "assigned_by_user_id")
    private Long assignedByUserId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public static AdminRoleAssignment assign(
            Long adminUserId,
            AdminRole role,
            Long assignedByUserId,
            LocalDateTime assignedAt
    ) {
        if (adminUserId == null || adminUserId <= 0 || role == null || assignedAt == null) {
            throw new IllegalArgumentException("관리자 역할 할당 정보가 올바르지 않습니다.");
        }
        return AdminRoleAssignment.builder()
                .adminUserId(adminUserId)
                .role(role)
                .status(AdminRoleAssignmentStatus.ACTIVE)
                .assignedByUserId(assignedByUserId)
                .assignedAt(assignedAt)
                .build();
    }

    public boolean isActive() {
        return status == AdminRoleAssignmentStatus.ACTIVE;
    }

    public boolean allows(AdminPermission permission) {
        return isActive() && role.allows(permission);
    }

    public void revoke(LocalDateTime revokedAt) {
        if (!isActive() || revokedAt == null || revokedAt.isBefore(assignedAt)) {
            throw new IllegalStateException("활성 관리자 역할만 유효한 시각에 회수할 수 있습니다.");
        }
        status = AdminRoleAssignmentStatus.REVOKED;
        this.revokedAt = revokedAt;
    }
}
