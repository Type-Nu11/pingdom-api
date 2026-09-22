package com.typenull.pingdom.identity.domain;

import java.util.Locale;

/**
 * 외부 로그인 제공자를 식별. 등록 ID는 공백 제거·대문자화 후 enum으로 변환하며 미지원 값은 거부.
 */
public enum AuthProvider {
    GOOGLE
    ;

    public static AuthProvider fromRegistrationId(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new IllegalArgumentException("OAuth2 registrationId가 비어있습니다.");
        }
        return AuthProvider.valueOf(registrationId.trim().toUpperCase(Locale.ROOT));
    }
}
