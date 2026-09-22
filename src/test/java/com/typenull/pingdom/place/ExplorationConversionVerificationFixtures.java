package com.typenull.pingdom.place;

import java.util.List;

public final class ExplorationConversionVerificationFixtures {
    /**
     * 추천 조회·클릭·설명 조회 계약을 정적 목록으로 제공하며 요청 간 상태를 가진 인스턴스는 만들지 않는다.
     */
    private ExplorationConversionVerificationFixtures() {}

    /**
     * 추천 조회·클릭·설명 조회의 순서와 기대 계약을 데이터로 제공합니다. 실제 HTTP 요청은 실행하지 않습니다.
     */
    public static List<ExplorationConversionVerificationScenario> scenarios() {
        return List.of(
                new ExplorationConversionVerificationScenario("explore", "GET", "/places/recommendations", 200,
                        List.of("requestId", "recommendation items")),
                new ExplorationConversionVerificationScenario("convert", "POST", "/places/recommendations/click", 201,
                        List.of("conversion event", "requestId correlation")),
                new ExplorationConversionVerificationScenario("verify", "GET", "/places/recommendations/{requestId}/explanation", 200,
                        List.of("explanation", "conversion reason"))
        );
    }
}
