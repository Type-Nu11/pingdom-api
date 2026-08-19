package com.typenull.pingdom.fixture.performance;

import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.place.application.service.place.PlaceSearchSort;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.util.List;
import java.util.Set;

public final class PingdomPerformanceLoadFixtures {

    public static final int NORMAL_PLACE_SEARCH_REQUESTS = 120;
    public static final int BOUNDARY_PAGE_LIMIT = 100;
    public static final int RETRY_EVENT_ATTEMPTS = 3;

    private PingdomPerformanceLoadFixtures() {
    }

    public static PingdomPerformanceLoadFixture realisticPlaceDiscoveryFixture() {
        return new PingdomPerformanceLoadFixture(
                users(),
                places(),
                reports(),
                retryEvents(),
                scenarios()
        );
    }

    private static List<FixtureUser> users() {
        return List.of(
                new FixtureUser(1L, "tourist-normal", UserRole.USER, true),
                new FixtureUser(2L, "tourist-boundary", UserRole.USER, true),
                new FixtureUser(3L, "merchant-owner", UserRole.MERCHANT_OWNER, true),
                new FixtureUser(4L, "admin-reviewer", UserRole.ADMIN, true),
                new FixtureUser(5L, "withdrawn-user", UserRole.USER, false)
        );
    }

    private static List<FixturePlace> places() {
        return List.of(
                new FixturePlace(
                        101L,
                        "성수 팝업 스토어",
                        3L,
                        37.5445d,
                        127.0557d,
                        PlaceOperatingStatus.OPERATING,
                        PlaceDiscoveryStatus.VISIBLE,
                        PlaceInformationSourceType.MERCHANT_OWNER,
                        PlaceInformationVerificationStatus.OWNER_SUBMITTED,
                        Set.of("K_POP", "POP_UP"),
                        32L,
                        18L,
                        4_800L,
                        384L,
                        PlaceSearchSort.POPULAR
                ),
                new FixturePlace(
                        102L,
                        "홍대 K-뷰티 쇼룸",
                        3L,
                        37.5563d,
                        126.9238d,
                        PlaceOperatingStatus.OPERATING,
                        PlaceDiscoveryStatus.VISIBLE,
                        PlaceInformationSourceType.ADMIN,
                        PlaceInformationVerificationStatus.ADMIN_VERIFIED,
                        Set.of("BEAUTY", "FASHION"),
                        54L,
                        41L,
                        7_200L,
                        720L,
                        PlaceSearchSort.NEAREST
                ),
                new FixturePlace(
                        103L,
                        "명동 임시 휴업 매장",
                        3L,
                        37.5637d,
                        126.9829d,
                        PlaceOperatingStatus.TEMPORARILY_CLOSED,
                        PlaceDiscoveryStatus.VISIBLE,
                        PlaceInformationSourceType.USER_REPORT,
                        PlaceInformationVerificationStatus.DISPUTED,
                        Set.of("FOOD"),
                        8L,
                        3L,
                        350L,
                        7L,
                        PlaceSearchSort.LATEST
                )
        );
    }

    private static List<FixtureReport> reports() {
        return List.of(
                new FixtureReport(
                        201L,
                        103L,
                        1L,
                        3L,
                        PlaceInformationReportTargetType.OPERATING_STATUS,
                        PlaceInformationReportReasonType.INCORRECT,
                        PlaceInformationReportStatus.DISPUTED,
                        PlaceInformationDisputeStatus.SUBMITTED,
                        "운영 상태 신고가 반박 대기 상태인지 식별"
                ),
                new FixtureReport(
                        202L,
                        101L,
                        2L,
                        null,
                        PlaceInformationReportTargetType.TOURIST_INFORMATION,
                        PlaceInformationReportReasonType.OUTDATED,
                        PlaceInformationReportStatus.SUBMITTED,
                        null,
                        "중복 신고 경계 조건 식별"
                )
        );
    }

    private static List<FixtureRetryEvent> retryEvents() {
        return List.of(
                new FixtureRetryEvent(
                        "place-information-report-201-retry",
                        OutboxEventType.PLACE_INFORMATION_REPORT_DISPUTED,
                        "PLACE",
                        103L,
                        RETRY_EVENT_ATTEMPTS,
                        true,
                        "반박 이벤트 발행 재시도 원인 식별"
                )
        );
    }

    private static List<PerformanceLoadScenario> scenarios() {
        return List.of(
                new PerformanceLoadScenario(
                        "place-search-normal-traffic",
                        PerformanceLoadScenarioType.NORMAL,
                        "GET /places",
                        NORMAL_PLACE_SEARCH_REQUESTS,
                        200,
                        null,
                        List.of("응답 장소 수", "정렬 기준", "운영 중 장소 노출")
                ),
                new PerformanceLoadScenario(
                        "place-search-boundary-page-limit",
                        PerformanceLoadScenarioType.BOUNDARY,
                        "GET /places?page=1&limit=100",
                        BOUNDARY_PAGE_LIMIT,
                        200,
                        null,
                        List.of("limit 상한", "totalPages", "hasNext")
                ),
                new PerformanceLoadScenario(
                        "place-discovery-hidden-place-exclusion",
                        PerformanceLoadScenarioType.BOUNDARY,
                        "GET /places?discoveryStatus=HIDDEN",
                        1,
                        200,
                        null,
                        List.of("hidden 장소 제외", "공개 장소만 반환", "totalElements")
                ),
                new PerformanceLoadScenario(
                        "place-recommendation-normal-ranking",
                        PerformanceLoadScenarioType.NORMAL,
                        "GET /places/recommendations?latitude=37.5445&longitude=127.0557",
                        NORMAL_PLACE_SEARCH_REQUESTS,
                        200,
                        null,
                        List.of("추천 장소 수", "추천 점수 내림차순", "노출 이벤트 기록")
                ),
                new PerformanceLoadScenario(
                        "place-recommendation-invalid-coordinate",
                        PerformanceLoadScenarioType.BOUNDARY,
                        "GET /places/recommendations?latitude=91&longitude=127.0557",
                        1,
                        400,
                        "INVALID_INPUT_VALUE",
                        List.of("위도 범위 검증", "ErrorResponse", "추천 조회 미실행")
                ),
                new PerformanceLoadScenario(
                        "report-dispute-forbidden",
                        PerformanceLoadScenarioType.FAILURE,
                        "POST /places/information-reports/{reportId}/disputes",
                        1,
                        403,
                        "PLACE_INFORMATION_DISPUTE_FORBIDDEN",
                        List.of("HTTP status", "error code", "dispute 미생성")
                ),
                new PerformanceLoadScenario(
                        "outbox-report-disputed-retry",
                        PerformanceLoadScenarioType.RETRY,
                        "Outbox PLACE_INFORMATION_REPORT_DISPUTED",
                        RETRY_EVENT_ATTEMPTS,
                        200,
                        null,
                        List.of("attempt count", "event type", "aggregate id")
                )
        );
    }
}
