package com.typenull.pingdom.fixture.place;

import java.util.List;

public final class PlaceVisitDecisionScenarioFixtures {

    private PlaceVisitDecisionScenarioFixtures() {
    }

    public static List<PlaceVisitDecisionScenario> scenarios() {
        return List.of(
                new PlaceVisitDecisionScenario(
                        "공개 중 장소의 방문 결정 데이터 조합",
                        PlaceVisitDecisionScenarioType.NORMAL,
                        200,
                        null,
                        "장소·Merchant·이벤트·예약·Offer 필드를 함께 반환한다"
                ),
                new PlaceVisitDecisionScenario(
                        "임시 휴업 장소 노출",
                        PlaceVisitDecisionScenarioType.BOUNDARY,
                        200,
                        null,
                        "TEMPORARILY_CLOSED 상태와 현재 운영 여부를 반환한다"
                ),
                new PlaceVisitDecisionScenario(
                        "인증되지 않은 요청",
                        PlaceVisitDecisionScenarioType.AUTHORIZATION,
                        401,
                        "INVALID_TOKEN",
                        "인증 실패 코드로 원인을 식별한다"
                ),
                new PlaceVisitDecisionScenario(
                        "숨김 또는 영구 폐업 장소 요청",
                        PlaceVisitDecisionScenarioType.FAILURE,
                        404,
                        "PLACE_NOT_FOUND",
                        "탐색 비노출 대상을 찾을 수 없음으로 처리한다"
                )
        );
    }
}
