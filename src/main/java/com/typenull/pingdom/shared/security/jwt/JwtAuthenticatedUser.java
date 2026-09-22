package com.typenull.pingdom.shared.security.jwt;

import java.util.function.Supplier;

/** JWT payload에서 추출해 SecurityContext에 저장한 사용자 식별 정보. 계정의 최신 상세 정보는 포함 대상에서 제외. */
public record JwtAuthenticatedUser(
        Long userId,
        String username
) {
    /** 인증 주체가 없을 때 호출자가 지정한 예외를 발생시킴. 값이 있으면 추가 권한 검사 없이 그대로 반환. */
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
