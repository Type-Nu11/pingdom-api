package com.typenull.pingdom.moderation.domain.audit;

public enum AdminAuditAction {
    USER_BAN_APPLIED,
    USER_BAN_RELEASED,
    REPORT_ACCEPTED,
    REPORT_DECLINED,
    POST_DELETED,
    PLACE_DELETED,
    PLACE_KAKAO_PLACE_ID_UPDATED,
    PLACE_MERGED,
    AD_CREATED,
    AD_DELETED
}
