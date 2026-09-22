package com.typenull.pingdom.integration.swagger.fixture;

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

/**
 * 호환성 fixture의 유형 범위와 저장 baseline의 경로·상태·오류 예시를 검증.
 */
class OpenApiCompatibilityFixturesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 각 OpenAPI 그룹에 정상·경계·실패·재시도 fixture가 모두 존재하는지 확인.
     */
    @Test
    void scenarioTypesPerDomain() {
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

    /**
     * 검증 설명이 비어 있지 않고 실패 fixture에는 명시적 오류 코드 또는 오류 응답 설명이 있는지 확인.
     */
    @Test
    void compatibilityDiagnostics() {
        assertThat(OpenApiCompatibilityFixtures.scenarios())
                .as("모든 계약 시나리오는 실패 원인을 식별할 assertion을 가져야 한다")
                .allSatisfy(scenario -> assertThat(scenario.assertions())
                        .as("%s assertions", scenario.name())
                        .isNotEmpty()
                        .allSatisfy(assertion -> assertThat(assertion).isNotBlank()));
        assertThat(OpenApiCompatibilityFixtures.scenarios())
                .filteredOn(scenario -> scenario.type() == OpenApiCompatibilityScenarioType.FAILURE)
                .allSatisfy(scenario -> assertThat(scenario.expectedErrorCode())
                        .as("%s 실패 시나리오는 오류 코드 또는 오류 응답 assertion을 가져야 한다", scenario.name())
                        .satisfies(code -> assertThat(code != null && !code.isBlank()
                                || scenario.assertions().stream().anyMatch(assertion -> assertion.contains("오류") || assertion.contains("ErrorResponse")))
                                .isTrue()));
    }

    /**
     * 저장된 baseline에서 fixture의 경로·메서드·응답 상태와 명시된 오류 코드가 존재하는지 확인. 실행 중인 API 응답 비교는 검증 범위에서 제외.
     */
    @Test
    void baselineOperations() throws IOException {
        for (OpenApiCompatibilityScenario scenario : OpenApiCompatibilityFixtures.scenarios()) {
            JsonNode document = readBaseline(scenario.domain());
            JsonNode operation = document.path("paths").path(scenario.path()).path(scenario.method().toLowerCase());
            assertThat(operation.isObject())
                    .as("%s fixture endpoint는 %s baseline에 존재해야 한다", scenario.name(), scenario.domain())
                    .isTrue();
            assertThat(operation.path("responses").has(String.valueOf(scenario.expectedStatus())))
                    .as("%s는 %s %s 응답을 계약에 포함해야 한다", scenario.name(), scenario.method(), scenario.expectedStatus())
                    .isTrue();
            if (scenario.expectedErrorCode() != null) {
                assertThat(operation.path("responses").findValues("code").stream()
                        .map(JsonNode::asText)
                        .toList())
                        .as("%s 실패 응답 오류 코드", scenario.name())
                        .contains(scenario.expectedErrorCode());
            }
        }
    }

    /**
     * fixture 이름과 그룹·시나리오 유형 조합의 유일성을 확인. 경로 자체의 유일성은 검증 범위에서 제외.
     */
    @Test
    void uniqueCompatibilityFixtures() {
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

    /**
     * 그룹에 해당하는 클래스패스 baseline JSON을 읽으며 리소스 누락 시 assertion으로 실패.
     */
    private JsonNode readBaseline(OpenApiCompatibilityDomain domain) throws IOException {
        String resource = "/openapi-baseline/" + domain.specName() + ".json";
        try (InputStream inputStream = getClass().getResourceAsStream(resource)) {
            assertThat(inputStream).as("baseline fixture resource: %s", resource).isNotNull();
            return objectMapper.readTree(inputStream);
        }
    }
}
