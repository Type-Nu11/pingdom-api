package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.HashSet;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingdomPerformanceLoadFixturesTest {

    /**
     * 사용자 역할·장소 3개 이상·반박자 있는 신고와 원인 문구가 있는 재시도 가능 반박 이벤트를 검증한다.
     */
    @Test
    void definesPerformanceFixtureRelationships() {
        PingdomPerformanceLoadFixture fixture = PingdomPerformanceLoadFixtures.realisticPlaceDiscoveryFixture();

        assertThat(fixture.users())
                .as("fixture는 관광객, 점주, 관리자 권한 시나리오를 모두 포함해야 한다")
                .extracting(FixtureUser::role)
                .contains(UserRole.USER, UserRole.MERCHANT_OWNER, UserRole.ADMIN);
        assertThat(fixture.places())
                .as("fixture는 탐색 부하를 만들 수 있는 복수 장소를 포함해야 한다")
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(fixture.reports())
                .as("fixture는 신고와 반박 상태를 모두 검증할 수 있어야 한다")
                .anySatisfy(report -> {
                    assertThat(report.status()).isEqualTo(PlaceInformationReportStatus.DISPUTED);
                    assertThat(report.disputedByUserId()).isNotNull();
                });
        assertThat(fixture.retryEvents())
                .as("재시도 시나리오는 원인 식별용 diagnosticReason을 포함해야 한다")
                .anySatisfy(event -> {
                    assertThat(event.eventType()).isEqualTo(OutboxEventType.PLACE_INFORMATION_REPORT_DISPUTED);
                    assertThat(event.retryable()).isTrue();
                    assertThat(event.diagnosticReason()).contains("재시도");
                });
    }

    /**
     * 정상·경계·실패·재시도 분류와 빈칸 없는 검증 라벨을 확인하고 실패 시나리오의 오류 코드 누락을 방지한다.
     */
    @Test
    void coversPerformanceScenarioCategories() {
        PingdomPerformanceLoadFixture fixture = PingdomPerformanceLoadFixtures.realisticPlaceDiscoveryFixture();

        assertThat(fixture.scenarios())
                .as("하위 이슈 요구사항의 정상, 경계, 실패, 재시도 시나리오를 모두 정의해야 한다")
                .extracting(PerformanceLoadScenario::type)
                .contains(
                        PerformanceLoadScenarioType.NORMAL,
                        PerformanceLoadScenarioType.BOUNDARY,
                        PerformanceLoadScenarioType.FAILURE,
                        PerformanceLoadScenarioType.RETRY
                );
        assertThat(fixture.scenarios())
                .as("실패 원인 식별을 위해 모든 시나리오에 구체 assertion 라벨이 있어야 한다")
                .allSatisfy(scenario -> assertThat(scenario.assertions())
                        .as("%s assertion labels", scenario.name())
                        .isNotEmpty()
                        .allSatisfy(assertion -> assertThat(assertion).isNotBlank()));
        assertThat(fixture.scenarios())
                .filteredOn(scenario -> scenario.type() == PerformanceLoadScenarioType.FAILURE)
                .as("실패 시나리오는 API 오류 코드를 명시해야 한다")
                .allSatisfy(scenario -> assertThat(scenario.expectedErrorCode()).isNotBlank());
    }

    /**
     * 사용자·장소·신고 ID가 각 목록에서 유일한지 검증해 권한과 정렬·상태 assertion의 대상 혼동을 방지한다.
     */
    @Test
    void usesUniquePerformanceIdentifiers() {
        PingdomPerformanceLoadFixture fixture = PingdomPerformanceLoadFixtures.realisticPlaceDiscoveryFixture();

        assertThat(fixture.users().stream().map(FixtureUser::id).toList())
                .as("사용자 fixture ID 중복은 권한 실패 원인 분석을 어렵게 한다")
                .hasSameSizeAs(new HashSet<>(fixture.users().stream().map(FixtureUser::id).toList()));
        assertThat(fixture.places().stream().map(FixturePlace::id).toList())
                .as("장소 fixture ID 중복은 정렬/페이징 assertion을 모호하게 한다")
                .hasSameSizeAs(new HashSet<>(fixture.places().stream().map(FixturePlace::id).toList()));
        assertThat(fixture.reports().stream().map(FixtureReport::id).toList())
                .as("신고 fixture ID 중복은 상태 전이 실패 원인 분석을 어렵게 한다")
                .hasSameSizeAs(new HashSet<>(fixture.reports().stream().map(FixtureReport::id).toList()));
    }

    /**
     * 공개 및 임시 휴업 장소, 중복되지 않는 정렬 기준, 추천 시나리오 2건과 검증 항목을 확인한다.
     * 이 테스트는 실제 숨김 장소 제외나 추천 API 실행을 검증하지 않는다.
     */
    @Test
    void coversDiscoveryRecommendationBoundaries() {
        PingdomPerformanceLoadFixture fixture = PingdomPerformanceLoadFixtures.realisticPlaceDiscoveryFixture();

        assertThat(fixture.places())
                .as("Discovery fixture는 공개 장소와 운영 경계 상태를 함께 가져야 한다")
                .anySatisfy(place -> assertThat(place.discoveryStatus()).isEqualTo(PlaceDiscoveryStatus.VISIBLE))
                .anySatisfy(place -> assertThat(place.operatingStatus()).isEqualTo(PlaceOperatingStatus.TEMPORARILY_CLOSED));
        assertThat(fixture.places())
                .as("Recommendation fixture는 서로 다른 위치와 추천 정렬 기준을 가져야 한다")
                .extracting(FixturePlace::expectedDefaultSort)
                .doesNotHaveDuplicates();
        assertThat(fixture.scenarios())
                .filteredOn(scenario -> scenario.endpoint().contains("/places/recommendations"))
                .as("Discovery와 Recommendation 계약 테스트 fixture에 추천 정상·경계 시나리오가 있어야 한다")
                .hasSize(2)
                .allSatisfy(scenario -> assertThat(scenario.assertions()).isNotEmpty());
    }
}
