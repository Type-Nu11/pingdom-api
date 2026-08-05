package com.typenull.pingdom.identity.domain.admin;

import java.util.Set;

public enum AdminRole {
    SUPER_ADMIN(Set.of()),
    CONTENT_MODERATOR(Set.of(
            AdminPermission.PLACE_READ,
            AdminPermission.PLACE_MODERATE,
            AdminPermission.REPORT_REVIEW
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
