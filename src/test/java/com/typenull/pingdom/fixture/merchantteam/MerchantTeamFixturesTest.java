package com.typenull.pingdom.fixture.merchantteam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class MerchantTeamFixturesTest {
    @Test
    void definesRealisticActorsAndPlaceOwnership() {
        MerchantTeamFixture fixture = MerchantTeamFixtures.realistic();
        assertThat(fixture.actors()).extracting(MerchantTeamActor::role).contains("OWNER", "EDITOR", "VIEWER");
        assertThat(fixture.actors()).anySatisfy(actor -> assertThat(actor.active()).isFalse());
        assertThat(fixture.places()).anySatisfy(place -> assertThat(place.ownerId()).isEqualTo(101L));
    }

    @Test
    void coversNormalBoundaryAuthorizationAndFailureContracts() {
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

    @Test
    void fixtureActorIdentifiersAreUniqueForDeterministicAssertions() {
        MerchantTeamFixture fixture = MerchantTeamFixtures.realistic();
        assertThat(fixture.actors().stream().map(MerchantTeamActor::id).toList())
                .hasSameSizeAs(new HashSet<>(fixture.actors().stream().map(MerchantTeamActor::id).toList()));
        assertThat(fixture.places().stream().map(MerchantTeamPlace::id).toList())
                .hasSameSizeAs(new HashSet<>(fixture.places().stream().map(MerchantTeamPlace::id).toList()));
    }
}
