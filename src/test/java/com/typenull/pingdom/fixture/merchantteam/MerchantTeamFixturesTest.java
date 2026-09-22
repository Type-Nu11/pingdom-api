package com.typenull.pingdom.fixture.merchantteam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MerchantTeamFixturesTest {
    /**
     * 팀 fixture에 OWNER·EDITOR·VIEWER, 비활성 사용자 및 점주 101의 장소가 포함되는지 검증한다.
     */
    @Test
    void definesActorsAndOwnership() {
        MerchantTeamFixture fixture = MerchantTeamFixtures.realistic();
        assertThat(fixture.actors()).extracting(MerchantTeamActor::role).contains("OWNER", "EDITOR", "VIEWER");
        assertThat(fixture.actors()).anySatisfy(actor -> assertThat(actor.active()).isFalse());
        assertThat(fixture.places()).anySatisfy(place -> assertThat(place.ownerId()).isEqualTo(101L));
    }

    /**
     * 정상·경계·인가·실패 분류와 GET/POST/PATCH 경로·비어 있지 않은 검증 항목을 확인한다.
     * 정상 이외 시나리오의 오류 코드 누락을 방지한다.
     */
    @Test
    void coversTeamScenarioContracts() {
        MerchantTeamFixture fixture = MerchantTeamFixtures.realistic();
        assertThat(fixture.scenarios()).extracting(MerchantTeamScenario::type)
                .contains(MerchantTeamScenarioType.NORMAL, MerchantTeamScenarioType.BOUNDARY,
                        MerchantTeamScenarioType.AUTHORIZATION, MerchantTeamScenarioType.FAILURE);
        assertThat(fixture.scenarios()).allSatisfy(scenario -> {
            assertThat(scenario.method()).isIn("GET", "POST", "PATCH");
            assertThat(scenario.endpoint()).startsWith("/merchant-owner/places/");
            assertThat(scenario.assertions()).isNotEmpty().allSatisfy(assertion -> assertThat(assertion).isNotBlank());
            if (scenario.type() != MerchantTeamScenarioType.NORMAL) {
                assertThat(scenario.expectedErrorCode()).as(scenario.name()).isNotBlank();
            }
        });
    }

    /**
     * 사용자와 장소 ID에 중복이 없는지 검증해 권한·소유 관계 assertion의 대상을 명확히 유지한다.
     */
    @Test
    void usesUniqueFixtureIdentifiers() {
        MerchantTeamFixture fixture = MerchantTeamFixtures.realistic();
        assertThat(fixture.actors().stream().map(MerchantTeamActor::id).toList())
                .hasSameSizeAs(new HashSet<>(fixture.actors().stream().map(MerchantTeamActor::id).toList()));
        assertThat(fixture.places().stream().map(MerchantTeamPlace::id).toList())
                .hasSameSizeAs(new HashSet<>(fixture.places().stream().map(MerchantTeamPlace::id).toList()));
    }
}
