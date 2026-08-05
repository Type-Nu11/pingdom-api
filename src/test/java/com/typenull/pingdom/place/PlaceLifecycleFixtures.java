package com.typenull.pingdom.place;

import java.util.List;

public final class PlaceLifecycleFixtures {
    private PlaceLifecycleFixtures() {
    }

    public static List<PlaceLifecycleScenario> scenarios() {
        return List.of(
                new PlaceLifecycleScenario("hide-place", "PATCH", "/admin/places/101/discovery-status", 200, null,
                        List.of("discoveryStatus HIDDEN", "검색 노출 제외", "audit 기록")),
                new PlaceLifecycleScenario("closed-place", "PATCH", "/admin/places/101/operating-status", 200, null,
                        List.of("operatingStatus PERMANENTLY_CLOSED", "추천 노출 제외", "상태 응답 유지")),
                new PlaceLifecycleScenario("owner-delete-place", "DELETE", "/places/101", 200, null,
                        List.of("소유자 삭제 허용", "연관 게시글 정리", "삭제 응답 메시지")),
                new PlaceLifecycleScenario("non-owner-delete-place", "DELETE", "/places/101", 403, "OTHERS_PLACE_NOT_DELETED",
                        List.of("타인 장소 삭제 차단", "데이터 보존", "ErrorResponse")),
                new PlaceLifecycleScenario("hidden-place-discovery", "GET", "/places?discoveryStatus=HIDDEN", 200, null,
                        List.of("공개 탐색 결과 제외", "페이지 메타데이터 일관성")),
                new PlaceLifecycleScenario("deleted-place-detail", "GET", "/places/101", 404, "PLACE_NOT_FOUND",
                        List.of("삭제 장소 비노출", "기존 404 계약", "오류 코드 식별"))
        );
    }
}
