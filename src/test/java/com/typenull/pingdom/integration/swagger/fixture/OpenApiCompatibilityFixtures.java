package com.typenull.pingdom.integration.swagger.fixture;

import java.util.List;

public final class OpenApiCompatibilityFixtures {

    private OpenApiCompatibilityFixtures() {
    }

    public static List<OpenApiCompatibilityScenario> scenarios() {
        return List.of(
                new OpenApiCompatibilityScenario(
                        "app-place-discovery-normal",
                        OpenApiCompatibilityDomain.APP,
                        OpenApiCompatibilityScenarioType.NORMAL,
                        "/places",
                        "GET",
                        200,
                        null,
                        List.of("장소 목록 응답 schema", "페이지네이션 필드", "운영 상태 enum")
                ),
                new OpenApiCompatibilityScenario(
                        "app-place-discovery-limit-boundary",
                        OpenApiCompatibilityDomain.APP,
                        OpenApiCompatibilityScenarioType.BOUNDARY,
                        "/places",
                        "GET",
                        200,
                        null,
                        List.of("limit 최대값", "page 경계", "빈 목록 응답")
                ),
                new OpenApiCompatibilityScenario(
                        "app-recommendation-invalid-request",
                        OpenApiCompatibilityDomain.APP,
                        OpenApiCompatibilityScenarioType.FAILURE,
                        "/places/recommendations",
                        "GET",
                        400,
                        null,
                        List.of("ErrorResponse", "좌표 범위 오류", "기존 응답 계약 유지")
                ),
                new OpenApiCompatibilityScenario(
                        "app-recommendation-contract-retry",
                        OpenApiCompatibilityDomain.APP,
                        OpenApiCompatibilityScenarioType.RETRY,
                        "/places/recommendations",
                        "GET",
                        200,
                        null,
                        List.of("동일 baseline 재검증", "diff 원인 보존", "재실행 가능한 fixture")
                ),
                new OpenApiCompatibilityScenario(
                        "common-login-normal",
                        OpenApiCompatibilityDomain.COMMON,
                        OpenApiCompatibilityScenarioType.NORMAL,
                        "/auth/login",
                        "POST",
                        200,
                        null,
                        List.of("로그인 응답 schema", "accessToken 필드", "refresh cookie 계약")
                ),
                new OpenApiCompatibilityScenario(
                        "common-signup-boundary",
                        OpenApiCompatibilityDomain.COMMON,
                        OpenApiCompatibilityScenarioType.BOUNDARY,
                        "/auth/signup",
                        "POST",
                        400,
                        null,
                        List.of("필수 입력값", "중복 이메일 경계", "ErrorResponse")
                ),
                new OpenApiCompatibilityScenario(
                        "common-login-failure",
                        OpenApiCompatibilityDomain.COMMON,
                        OpenApiCompatibilityScenarioType.FAILURE,
                        "/auth/login",
                        "POST",
                        401,
                        "INVALID_CREDENTIALS",
                        List.of("인증 실패 상태", "오류 코드", "민감 정보 비노출")
                ),
                new OpenApiCompatibilityScenario(
                        "common-refresh-retry",
                        OpenApiCompatibilityDomain.COMMON,
                        OpenApiCompatibilityScenarioType.RETRY,
                        "/auth/token/refresh",
                        "POST",
                        200,
                        null,
                        List.of("재시도 가능한 계약", "동일 응답 schema", "토큰 회전 필드")
                ),
                new OpenApiCompatibilityScenario(
                        "web-dashboard-normal",
                        OpenApiCompatibilityDomain.WEB,
                        OpenApiCompatibilityScenarioType.NORMAL,
                        "/admin/dashboard/summary",
                        "GET",
                        200,
                        null,
                        List.of("관리자 요약 응답", "최근 집계 필드", "대시보드 schema")
                ),
                new OpenApiCompatibilityScenario(
                        "web-pending-items-boundary",
                        OpenApiCompatibilityDomain.WEB,
                        OpenApiCompatibilityScenarioType.BOUNDARY,
                        "/admin/dashboard/pending-items",
                        "GET",
                        200,
                        null,
                        List.of("빈 목록 []", "reportId와 postId 구분", "type 허용 목록")
                ),
                new OpenApiCompatibilityScenario(
                        "web-dashboard-forbidden",
                        OpenApiCompatibilityDomain.WEB,
                        OpenApiCompatibilityScenarioType.FAILURE,
                        "/admin/dashboard/summary",
                        "GET",
                        403,
                        null,
                        List.of("관리자 권한 오류", "ErrorResponse", "응답 형식 유지")
                ),
                new OpenApiCompatibilityScenario(
                        "web-dashboard-contract-retry",
                        OpenApiCompatibilityDomain.WEB,
                        OpenApiCompatibilityScenarioType.RETRY,
                        "/admin/dashboard/recent-activities",
                        "GET",
                        200,
                        null,
                        List.of("baseline 재생성 재시도", "createdAt 필드", "diff 원인 식별")
                )
        );
    }
}
