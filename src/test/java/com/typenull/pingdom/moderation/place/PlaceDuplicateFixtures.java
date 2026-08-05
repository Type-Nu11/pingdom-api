package com.typenull.pingdom.moderation.place;

import java.util.List;

public final class PlaceDuplicateFixtures {
    private PlaceDuplicateFixtures() {
    }

    public static List<PlaceDuplicateScenario> scenarios() {
        return List.of(
                new PlaceDuplicateScenario("candidate-list-normal", "GET", "/admin/places/duplicate-candidates?status=PENDING", 200, null,
                        List.of("PENDING 후보 목록", "confidence 정렬", "페이지 응답")),
                new PlaceDuplicateScenario("candidate-confirm-normal", "POST", "/admin/places/duplicate-candidates/301/confirm", 200, null,
                        List.of("CONFIRMED 전이", "reviewNote 보존", "감사 이력")),
                new PlaceDuplicateScenario("candidate-reject-boundary", "POST", "/admin/places/duplicate-candidates/301/reject", 200, null,
                        List.of("REJECTED 전이", "병합 불가 유지", "중복 pair 보존")),
                new PlaceDuplicateScenario("candidate-merge-normal", "POST", "/admin/places/duplicate-candidates/301/merge", 200, null,
                        List.of("대상 장소 유지", "원본 참조 이동", "병합 이력 생성")),
                new PlaceDuplicateScenario("candidate-merge-pending-failure", "POST", "/admin/places/duplicate-candidates/302/merge", 409,
                        "PLACE_MERGE_NOT_ALLOWED", List.of("확정 후보만 병합", "원본 보존", "오류 코드 식별")),
                new PlaceDuplicateScenario("candidate-not-found", "GET", "/admin/places/duplicate-candidates/999", 404,
                        "PLACE_DUPLICATE_CANDIDATE_NOT_FOUND", List.of("존재하지 않는 후보", "ErrorResponse")),
                new PlaceDuplicateScenario("non-admin-forbidden", "GET", "/admin/places/duplicate-candidates", 403,
                        "FORBIDDEN", List.of("ADMIN 역할 검증", "민감한 후보 정보 비공개"))
        );
    }
}
