package com.typenull.pingdom.migration.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayBackfillFixturesTest {

    @Test
    void coversNormalBoundaryFailureAndRetryScenarios() {
        List<FlywayBackfillScenario> scenarios = FlywayBackfillFixtures.scenarios();

        assertThat(scenarios.stream()
                .map(FlywayBackfillScenario::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FlywayBackfillScenarioType.class))))
                .as("backfill fixture는 정상·경계·실패·재시도 시나리오를 모두 가져야 한다")
                .containsExactlyInAnyOrder(FlywayBackfillScenarioType.values());
    }

    @Test
    void hasRealisticLegacyDataAndDiagnosticAssertions() {
        assertThat(FlywayBackfillFixtures.scenarios())
                .as("모든 migration fixture는 기존 데이터와 backfill 기대값을 설명해야 한다")
                .allSatisfy(scenario -> {
                    assertThat(scenario.legacyData()).as("%s legacy data", scenario.name()).isNotBlank();
                    assertThat(scenario.expectedBackfill()).as("%s expected backfill", scenario.name()).isNotBlank();
                    assertThat(scenario.assertions())
                            .as("%s assertions", scenario.name())
                            .isNotEmpty()
                            .allSatisfy(assertion -> assertThat(assertion).isNotBlank());
                });
        assertThat(FlywayBackfillFixtures.scenarios())
                .filteredOn(scenario -> scenario.type() == FlywayBackfillScenarioType.FAILURE)
                .allSatisfy(scenario -> assertThat(scenario.expectedFailureReason())
                        .as("%s 실패 원인", scenario.name())
                        .isNotBlank());
    }

    @Test
    void fixtureIdentifiersAndMigrationTargetsAreValid() {
        List<FlywayBackfillScenario> scenarios = FlywayBackfillFixtures.scenarios();

        assertThat(scenarios.stream().map(FlywayBackfillScenario::name).toList())
                .as("migration fixture 이름은 중복되면 안 된다")
                .hasSameSizeAs(new HashSet<>(scenarios.stream().map(FlywayBackfillScenario::name).toList()));
        assertThat(scenarios)
                .allSatisfy(scenario -> {
                    assertThat(scenario.migrationVersion()).matches("\\d+");
                    assertThat(scenario.targetTable()).isNotBlank();
                });
    }

    @Test
    void referencesExistingMigrationScripts() {
        for (FlywayBackfillScenario scenario : FlywayBackfillFixtures.scenarios()) {
            String prefix = "V" + scenario.migrationVersion() + "_";
            assertThat(findMigrationResource(prefix))
                    .as("fixture가 참조하는 migration V%s가 존재해야 한다", scenario.migrationVersion())
                    .isTrue();
        }
    }

    private boolean findMigrationResource(String prefix) {
        String[] migrationNames = {
                "V23__add_created_at_to_post_report.sql",
                "V30__normalize_map_place_address_and_geocoding_source.sql",
                "V42__add_merchant_place_claim_ownership_transfer.sql",
                "V43__validate_merchant_place_claim_ownership_transfer_constraints.sql",
                "V54__add_place_information_source_evidence_metadata.sql",
                "V56__create_place_media.sql"
        };
        for (String migrationName : migrationNames) {
            if (migrationName.startsWith(prefix)) {
                return getClass().getResourceAsStream("/db/migration/" + migrationName) != null;
            }
        }
        return false;
    }
}
