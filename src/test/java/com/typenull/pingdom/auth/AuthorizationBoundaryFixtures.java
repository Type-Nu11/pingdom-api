package com.typenull.pingdom.auth;

import java.util.List;

public final class AuthorizationBoundaryFixtures {
    private AuthorizationBoundaryFixtures() {
    }

    public static List<AuthorizationBoundaryScenario> scenarios() {
        return List.of(
                new AuthorizationBoundaryScenario("anonymous-login", "POST", "/auth/login", "ANONYMOUS", 200, null,
                        List.of("인증 공개", "로그인 응답 계약")),
                new AuthorizationBoundaryScenario("anonymous-protected-api", "GET", "/users/me", "ANONYMOUS", 401, "INVALID_TOKEN",
                        List.of("JWT 필수", "공통 ErrorResponse")),
                new AuthorizationBoundaryScenario("user-public-discovery", "GET", "/places", "USER", 200, null,
                        List.of("일반 사용자 접근", "장소 목록 응답")),
                new AuthorizationBoundaryScenario("user-admin-api", "GET", "/admin/dashboard/pending-items", "USER", 403, "FORBIDDEN",
                        List.of("ADMIN 역할 차단", "감사 대상 API 보호")),
                new AuthorizationBoundaryScenario("merchant-owner-api", "GET", "/merchant-owner/me", "MERCHANT_OWNER", 200, null,
                        List.of("Merchant Owner 접근", "활성 계정 검증")),
                new AuthorizationBoundaryScenario("admin-api", "GET", "/admin/dashboard/pending-items", "ADMIN", 200, null,
                        List.of("ADMIN 접근", "관리자 응답 계약"))
        );
    }
}
