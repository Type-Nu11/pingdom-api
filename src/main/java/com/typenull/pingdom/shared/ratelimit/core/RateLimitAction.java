package com.typenull.pingdom.shared.ratelimit.core;

/** 요청 인자·쿠키·인증 사용자에서 제한 식별자를 추출할 때 사용하는 행위 분류. */
public enum RateLimitAction {
    SIGNUP,
    LOGIN,
    TOKEN_REFRESH,
    EMAIL_RESEND,
    EMAIL_VERIFY,
    PASSWORD_RESET_REQUEST,
    PASSWORD_RESET_CONFIRM,
    POST_REPORT,
    MAP_IMAGE_LIKE,
    RECOMMENDATION_CLICK,
    IMAGE_UPLOAD,
    CONSULTATION_INTRO,
    LOCATION_ANALYSIS_REPORT
}
