package com.typenull.pingdom.identity.domain.admin;

import java.util.Set;

/**
 * 관리자 업무 역할을 세부 권한 집합으로 해석.
 * SUPER_ADMIN은 내부 빈 집합과 무관하게 모든 권한을 허용하고 전체 권한 목록을 반환.
 */
public enum AdminRole {
    SUPER_ADMIN(Set.of()),
    CONTENT_MODERATOR(Set.of(
            AdminPermission.PLACE_READ,
            AdminPermission.PLACE_MODERATE,
            AdminPermission.REPORT_REVIEW,
            AdminPermission.SCOUT_REVIEW
    )),
    MERCHANT_OPERATOR(Set.of(
            AdminPermission.PLACE_READ,
            AdminPermission.MERCHANT_REVIEW
    )),
    SUPPORT_OPERATOR(Set.of(
            AdminPermission.USER_READ,
            AdminPermission.USER_SANCTION
    )),
    ANALYST(Set.of(
            AdminPermission.DASHBOARD_READ,
            AdminPermission.AUDIT_READ
    ));

    private final Set<AdminPermission> permissions;

    AdminRole(Set<AdminPermission> permissions) {
        this.permissions = permissions;
    }

    public boolean allows(AdminPermission permission) {
        return this == SUPER_ADMIN || permissions.contains(permission);
    }

    public Set<AdminPermission> permissions() {
        return this == SUPER_ADMIN ? Set.of(AdminPermission.values()) : permissions;
    }
}
