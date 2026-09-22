package com.typenull.pingdom.integration.migration.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * backfill 시나리오 설명의 완전성과 식별자·migration 리소스 참조를 검증. SQL 실행은 검증 범위에서 제외.
 */
class FlywayBackfillFixturesTest {

    /**
     * backfill fixture에 정상·경계·실패·재시도 유형이 빠짐없이 포함되는지 확인.
     */
    @Test
    void allBackfillScenarioTypes() {
        List<FlywayBackfillScenario> scenarios = FlywayBackfillFixtures.scenarios();

        assertThat(scenarios.stream()
                .map(FlywayBackfillScenario::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(FlywayBackfillScenarioType.class))))
                .as("backfill fixture는 정상·경계·실패·재시도 시나리오를 모두 가져야 한다")
                .containsExactlyInAnyOrder(FlywayBackfillScenarioType.values());
    }

    /**
     * 각 fixture의 기존 데이터·기대값·검증 설명과 실패 시나리오의 원인이 비어 있지 않은지 확인.
     */
    @Test
    void backfillDiagnostics() {
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

    /**
     * fixture 이름 유일성, 숫자 migration 버전, 대상 테이블 이름의 존재를 확인.
     */
    @Test
    void backfillIdentifiers() {
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

    /**
     * fixture의 migration 버전에 대응하는 등록 SQL 리소스가 클래스패스에 있는지 확인.
     */
    @Test
    void referencesExistingMigrationScripts() {
        for (FlywayBackfillScenario scenario : FlywayBackfillFixtures.scenarios()) {
            String prefix = "V" + scenario.migrationVersion() + "_";
            assertThat(findMigrationResource(prefix))
                    .as("fixture가 참조하는 migration V%s가 존재해야 한다", scenario.migrationVersion())
                    .isTrue();
        }
    }

    /**
     * 명시된 migration 파일 목록에서 버전 접두사가 맞는 첫 리소스의 존재만 확인. 전체 migration 디렉터리 탐색은 제외.
     */
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
