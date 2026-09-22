package com.typenull.pingdom.place.infrastructure.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverPlaceAdministrativeRegionResolverCacheTest {

    private static final String FIRST_REQUEST_URL = "https://naver.test/map-reversegeocode/v2/gc?request=coordsToaddr"
            + "&coords=127.0473,37.5172&sourcecrs=epsg:4326&orders=legalcode&output=json";
    private static final String SECOND_REQUEST_URL = "https://naver.test/map-reversegeocode/v2/gc?request=coordsToaddr"
            + "&coords=127.0474,37.5173&sourcecrs=epsg:4326&orders=legalcode&output=json";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    /** 네이버 HTTP 요청 횟수와 응답을 제어하는 대역 서버를 매 테스트 초기화. */
    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    /** 같은 좌표의 연속 성공 조회를 한 번의 HTTP 대역 요청으로 처리하는지 확인. */
    @Test
    void cachesSuccessfulRegionLookup() {
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 10_000);

        assertThat(resolver.resolve(37.5172, 127.0473).code()).isEqualTo("11680");
        assertThat(resolver.resolve(37.5172, 127.0473).code()).isEqualTo("11680");

        server.verify();
    }

    /** TTL 1밀리초 설정 후 5밀리초를 기다리면 같은 좌표를 다시 외부 조회하는지 확인. */
    @Test
    void reloadsExpiredRegionCache() throws InterruptedException {
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMillis(1), 10_000);

        resolver.resolve(37.5172, 127.0473);
        Thread.sleep(5);
        resolver.resolve(37.5172, 127.0473);

        server.verify();
    }

    /** 용량 1에서 두 좌표를 순서대로 조회한 뒤 첫 좌표를 재조회하면 세 번째 HTTP 요청이 발생하는지 확인. */
    @Test
    void clearsCacheAtCapacity() {
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(SECOND_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 1);

        resolver.resolve(37.5172, 127.0473);
        resolver.resolve(37.5173, 127.0474);
        resolver.resolve(37.5172, 127.0473);

        server.verify();
    }

    /** 결과 없음 응답은 반복 조회마다 HTTP를 요청하고 같은 지역 없음 오류로 변환되는지 확인. */
    @Test
    void doesNotCacheMissingRegion() {
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":3},\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":3},\"results\":[]}", MediaType.APPLICATION_JSON));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 10_000);

        assertRegionNotFound(resolver);
        assertRegionNotFound(resolver);

        server.verify();
    }

    /** 429 응답을 외부 조회 실패로 바꾸고 다음 요청에서도 다시 HTTP를 호출하는지 확인. */
    @Test
    void doesNotCacheProviderFailure() {
        server.expect(requestTo(FIRST_REQUEST_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(FIRST_REQUEST_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 10_000);

        assertResolutionFailed(resolver);
        assertResolutionFailed(resolver);

        server.verify();
    }

    /** 비활성 resolver와 비밀키 누락 resolver 모두 사용 불가 오류를 내고 HTTP 요청을 보내지 않는지 확인. */
    @Test
    void skipsUnconfiguredRegionRequests() {
        NaverPlaceAdministrativeRegionResolver disabledResolver = new NaverPlaceAdministrativeRegionResolver(
                builder.build(),
                new NaverLocalRegionProperties(false, "test-client-id", "test-client-secret", "https://naver.test",
                        null, null, null, null)
        );
        NaverPlaceAdministrativeRegionResolver missingCredentialResolver = new NaverPlaceAdministrativeRegionResolver(
                builder.build(),
                new NaverLocalRegionProperties(true, "test-client-id", null, "https://naver.test", null, null, null, null)
        );

        assertThatThrownBy(() -> disabledResolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE));
        assertThatThrownBy(() -> missingCredentialResolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE));

        server.verify();
    }

    /** TTL과 최대 항목 수를 지정하고 나머지 인증·주소는 고정한 resolver를 생성. */
    private NaverPlaceAdministrativeRegionResolver resolver(Duration cacheTtl, int cacheMaxEntries) {
        return new NaverPlaceAdministrativeRegionResolver(
                builder.build(),
                new NaverLocalRegionProperties(
                        true,
                        "test-client-id",
                        "test-client-secret",
                        "https://naver.test",
                        null,
                        null,
                        cacheTtl,
                        cacheMaxEntries
                )
        );
    }

    /** 강남구를 가리키는 정상 legalcode JSON을 반환해 캐시 동작에 집중. */
    private String successResponse() {
        return """
                {
                  "status": {"code": 0},
                  "results": [{
                    "name": "legalcode",
                    "code": {"id": "1168010100"},
                    "region": {
                      "area1": {"name": "서울특별시"},
                      "area2": {"name": "강남구"}
                    }
                  }]
                }
                """;
    }

    /** 고정 좌표 재조회가 지역 없음 오류로 끝나는지 확인. */
    private void assertRegionNotFound(NaverPlaceAdministrativeRegionResolver resolver) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND));
    }

    /** 고정 좌표 재조회가 외부 조회 실패 오류로 끝나는지 확인. */
    private void assertResolutionFailed(NaverPlaceAdministrativeRegionResolver resolver) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED));
    }
}
