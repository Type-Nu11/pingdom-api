package com.typenull.pingdom.swagger.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpenApiCompatibilityFixturesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void coversAllRequiredScenarioTypesForEachDomain() {
        List<OpenApiCompatibilityScenario> scenarios = OpenApiCompatibilityFixtures.scenarios();

        for (OpenApiCompatibilityDomain domain : OpenApiCompatibilityDomain.values()) {
            assertThat(scenarios.stream()
                    .filter(scenario -> scenario.domain() == domain)
                    .map(OpenApiCompatibilityScenario::type)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(OpenApiCompatibilityScenarioType.class))))
                    .as("%s 도메인은 정상·경계·실패·재시도 fixture를 모두 가져야 한다", domain)
                    .containsExactlyInAnyOrder(OpenApiCompatibilityScenarioType.values());
        }
    }

    @Test
    void hasDiagnosticAssertionsAndFailureCodes() {
        assertThat(OpenApiCompatibilityFixtures.scenarios())
                .as("모든 계약 시나리오는 실패 원인을 식별할 assertion을 가져야 한다")
                .allSatisfy(scenario -> assertThat(scenario.assertions())
                        .as("%s assertions", scenario.name())
                        .isNotEmpty()
                        .allSatisfy(assertion -> assertThat(assertion).isNotBlank()));
        assertThat(OpenApiCompatibilityFixtures.scenarios())
                .filteredOn(scenario -> scenario.type() == OpenApiCompatibilityScenarioType.FAILURE)
                .allSatisfy(scenario -> assertThat(scenario.expectedErrorCode())
                        .as("%s 실패 시나리오 오류 코드", scenario.name())
                        .isNotBlank());
    }

    @Test
    void pointsToExistingPathsInEachDomainBaseline() throws IOException {
        for (OpenApiCompatibilityScenario scenario : OpenApiCompatibilityFixtures.scenarios()) {
            JsonNode document = readBaseline(scenario.domain());
            assertThat(document.path("paths").has(scenario.path()))
                    .as("%s fixture endpoint는 %s baseline에 존재해야 한다", scenario.name(), scenario.domain())
                    .isTrue();
        }
    }

    @Test
    void fixtureIdentifiersAndPathsAreUniquePerDomainAndScenarioType() {
        List<OpenApiCompatibilityScenario> scenarios = OpenApiCompatibilityFixtures.scenarios();
        assertThat(scenarios.stream().map(OpenApiCompatibilityScenario::name).toList())
                .as("fixture 이름은 실패 원인 추적을 위해 중복되면 안 된다")
                .hasSameSizeAs(new HashSet<>(scenarios.stream().map(OpenApiCompatibilityScenario::name).toList()));
        assertThat(scenarios.stream()
                .map(scenario -> scenario.domain() + ":" + scenario.type())
                .toList())
                .as("도메인별 시나리오 유형은 중복되면 안 된다")
                .hasSameSizeAs(new HashSet<>(scenarios.stream()
                        .map(scenario -> scenario.domain() + ":" + scenario.type())
                        .toList()));
    }

    private JsonNode readBaseline(OpenApiCompatibilityDomain domain) throws IOException {
        String resource = "/openapi-baseline/" + domain.specName() + ".json";
        try (InputStream inputStream = getClass().getResourceAsStream(resource)) {
            assertThat(inputStream).as("baseline fixture resource: %s", resource).isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }
}
