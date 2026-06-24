package com.typenull.pingdom.moderation.domain.audit;

public enum AdminAuditAction {
    USER_BAN_APPLIED,
    USER_BAN_RELEASED,
    REPORT_ACCEPTED,
    REPORT_DECLINED,
    POST_DELETED,
    POST_HIDDEN,
    POST_RESTORED,
    APPEAL_APPROVED,
    APPEAL_REJECTED,
    PLACE_DELETED,
    PLACE_MERGED,
    AD_CREATED,
    AD_DELETED
}
