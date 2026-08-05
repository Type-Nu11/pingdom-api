package com.typenull.pingdom.place;

import java.util.List;

public final class ExplorationConversionVerificationFixtures {
    private ExplorationConversionVerificationFixtures() {}

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
