package com.typenull.pingdom.fixture.moderation.trust;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.fixture.moderation.trust.fixture.TrustLayerFixtures;
import com.typenull.pingdom.fixture.moderation.trust.fixture.TrustReporterFixture;
import com.typenull.pingdom.fixture.moderation.trust.fixture.TrustScenario;
import com.typenull.pingdom.fixture.moderation.trust.fixture.TrustScenarioType;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrustLayerScenarioAutomationTest {

    private static final TrustReporterFixture FIXTURE = TrustLayerFixtures.realisticReporterFixture();

    /**
     * 각 공급 시나리오의 이름·HTTP 경로 형식·상태 범위·진단 항목을 확인한다.
     * FAILURE는 400 이상과 오류 코드를, 나머지는 400 미만과 null 오류 코드를 가져야 한다. 실제 HTTP 호출 검증은 아니다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void validatesTrustScenarioContract(String name, TrustScenario scenario) {
        assertThat(scenario.name()).isEqualTo(name);
        assertThat(scenario.endpoint()).matches("(GET|POST|PATCH|PUT|DELETE) .+");
        assertThat(scenario.expectedStatus()).isBetween(200, 499);
        assertThat(scenario.assertions()).isNotEmpty().allSatisfy(assertion ->
                assertThat(assertion).as("diagnostic assertion").isNotBlank());

        if (scenario.type() == TrustScenarioType.FAILURE) {
            assertThat(scenario.expectedStatus()).isGreaterThanOrEqualTo(400);
            assertThat(scenario.expectedErrorCode()).isNotBlank();
        } else {
            assertThat(scenario.expectedStatus()).isLessThan(400);
            assertThat(scenario.expectedErrorCode()).isNull();
        }
    }

    /**
     * 신뢰도 fixture가 정상 1건·경계 1건·실패 2건을 정확히 제공하는지 검증한다.
     */
    @Test
    void coversTrustExecutionCategories() {
        assertThat(FIXTURE.scenarios()).extracting(TrustScenario::type)
                .containsExactlyInAnyOrder(
                        TrustScenarioType.NORMAL,
                        TrustScenarioType.BOUNDARY,
                        TrustScenarioType.FAILURE,
                        TrustScenarioType.FAILURE
                );
    }

    /**
     * 각 신뢰도 계약을 이름과 시나리오 객체의 Arguments로 바꾸어 MethodSource의 표시 이름과 입력을 공급한다.
     */
    private static Stream<Arguments> scenarios() {
        return FIXTURE.scenarios().stream()
                .map(scenario -> Arguments.of(scenario.name(), scenario));
    }
}
