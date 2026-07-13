package com.typenull.pingdom.shared.security.jwt;

import java.util.function.Supplier;

// JWT 인증 완료 사용자 정보 전달 record
public record JwtAuthenticatedUser(
        Long userId,
        String username
) {
    public static <X extends RuntimeException> JwtAuthenticatedUser require(
            JwtAuthenticatedUser user,
            Supplier<X> exceptionSupplier
    ) {
        if (user == null) {
            throw exceptionSupplier.get();
        }
        return user;
    }
}
