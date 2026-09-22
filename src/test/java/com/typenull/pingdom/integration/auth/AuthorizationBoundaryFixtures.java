package com.typenull.pingdom.integration.auth;

import java.util.List;

/**
 * 역할별 대표 인증 경계를 설명하는 정적 시나리오 목록을 제공한다.
 */
public final class AuthorizationBoundaryFixtures {
    /**
     * 역할별 인증 경계는 정적 시나리오 목록으로 제공하므로 인스턴스 상태를 두지 않고 생성을 막는다.
     */
    private AuthorizationBoundaryFixtures() {
    }

    /**
     * 공개 로그인과 보호 API에 대한 익명·일반 사용자·상점 소유자·관리자 기대 계약을 제공한다. 이 목록 자체는 HTTP 요청을 실행하지 않는다.
     */
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
