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

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void 동일_좌표의_성공_결과는_TTL_동안_캐시한다() {
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess(successResponse(), MediaType.APPLICATION_JSON));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 10_000);

        assertThat(resolver.resolve(37.5172, 127.0473).code()).isEqualTo("11680");
        assertThat(resolver.resolve(37.5172, 127.0473).code()).isEqualTo("11680");

        server.verify();
    }

    @Test
    void TTL이_만료되면_외부_조회한다() throws InterruptedException {
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

    @Test
    void 캐시_용량에_도달하면_기존_캐시를_비우고_새_결과를_저장한다() {
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

    @Test
    void 지역_없음_결과는_캐시하지_않는다() {
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":3},\"results\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FIRST_REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":3},\"results\":[]}", MediaType.APPLICATION_JSON));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 10_000);

        assertRegionNotFound(resolver);
        assertRegionNotFound(resolver);

        server.verify();
    }

    @Test
    void HTTP_오류는_외부_조회_실패로_변환하고_캐시하지_않는다() {
        server.expect(requestTo(FIRST_REQUEST_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(FIRST_REQUEST_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        NaverPlaceAdministrativeRegionResolver resolver = resolver(Duration.ofMinutes(10), 10_000);

        assertResolutionFailed(resolver);
        assertResolutionFailed(resolver);

        server.verify();
    }

    @Test
    void 비활성화되었거나_인증_정보가_누락되면_외부_호출하지_않는다() {
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

    private void assertRegionNotFound(NaverPlaceAdministrativeRegionResolver resolver) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND));
    }

    private void assertResolutionFailed(NaverPlaceAdministrativeRegionResolver resolver) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED));
    }
}
