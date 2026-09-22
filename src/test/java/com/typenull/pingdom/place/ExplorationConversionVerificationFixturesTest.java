package com.typenull.pingdom.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class ExplorationConversionVerificationFixturesTest {
    /**
     * 탐색·전환·검증 fixture 세 개의 순서와 필수 메타데이터를 확인. 실제 전체 API 흐름 실행은 검증 범위에서 제외.
     */
    @Test
    void definesExplorationLoopFixtures() {
        var scenarios = ExplorationConversionVerificationFixtures.scenarios();

        assertThat(scenarios).hasSize(3);
        assertThat(scenarios).extracting(ExplorationConversionVerificationScenario::name)
                .containsExactly("explore", "convert", "verify");
        assertThat(scenarios).allSatisfy(scenario -> {
            assertThat(scenario.path()).isNotBlank();
            assertThat(scenario.expectedStatus()).isBetween(200, 299);
            assertThat(scenario.assertions()).isNotEmpty();
        });
    }

    /**
     * 각 시나리오의 HTTP 메서드·경로·성공 상태·검증 문구가 유효한 형식인지 확인.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void verifiesEachLoopContract(ExplorationConversionVerificationScenario scenario) {
        assertThat(scenario.method()).isIn("GET", "POST");
        assertThat(scenario.path()).startsWith("/");
        assertThat(scenario.expectedStatus()).isBetween(200, 299);
        assertThat(scenario.assertions()).allSatisfy(assertion -> assertThat(assertion).isNotBlank());
    }

    /**
     * 공통 시나리오 목록을 파라미터 테스트의 입력 스트림으로 제공.
     */
    private static Stream<ExplorationConversionVerificationScenario> scenarios() {
        return ExplorationConversionVerificationFixtures.scenarios().stream();
    }
}
