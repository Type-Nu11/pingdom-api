package com.typenull.pingdom.place;

import java.util.List;

public record ExplorationConversionVerificationScenario(
        String name, String method, String path, int expectedStatus, List<String> assertions) {
}
