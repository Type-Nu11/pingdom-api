package com.typenull.pingdom.domain.auth.domain;

import java.util.Locale;

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
