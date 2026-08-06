package com.typenull.pingdom.moderation.place;

import java.util.List;

public record PlaceDuplicateScenario(String name, String method, String endpoint, int expectedStatus,
                                     String expectedErrorCode, List<String> assertions) {
}
