package com.typenull.pingdom.place;

import java.util.List;

public final class ExplorationConversionVerificationFixtures {
    /**
     * 추천 조회·클릭·설명 조회 계약의 정적 목록 제공을 위한 유틸리티 생성자. 요청 간 상태를 보관하는 인스턴스 생성 차단.
     */
    private ExplorationConversionVerificationFixtures() {}

    /**
     * 추천 조회·클릭·설명 조회의 순서와 기대 계약을 데이터로 제공. 실제 HTTP 요청 실행은 제외.
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
