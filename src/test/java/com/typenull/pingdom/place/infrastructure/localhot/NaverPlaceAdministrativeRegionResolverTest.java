package com.typenull.pingdom.place.infrastructure.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverPlaceAdministrativeRegionResolverTest {

    private static final String REQUEST_URL = "https://naver.test/map-reversegeocode/v2/gc?request=coordsToaddr"
            + "&coords=127.0473,37.5172&sourcecrs=epsg:4326&orders=legalcode&output=json";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void legalcode의_codeId와_area1_area2를_기존_지역_계약으로_변환한다() {
        server.expect(requestTo(REQUEST_URL))
                .andExpect(header("x-ncp-apigw-api-key-id", "test-client-id"))
                .andExpect(header("x-ncp-apigw-api-key", "test-client-secret"))
                .andRespond(withSuccess(successResponse("1168010100", "서울특별시", "강남구"), MediaType.APPLICATION_JSON));

        var region = resolver(true, "test-client-id", "test-client-secret").resolve(37.5172, 127.0473);

        assertThat(region.code()).isEqualTo("11680");
        assertThat(region.sido()).isEqualTo("서울특별시");
        assertThat(region.sigungu()).isEqualTo("강남구");
        assertThat(region.regionName()).isEqualTo("서울특별시 강남구");
        server.verify();
    }

    @Test
    void 세종은_빈_area2를_세종특별자치시_지역으로_처리한다() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess(successResponse("3611010700", "세종특별자치시", ""), MediaType.APPLICATION_JSON));

        var region = resolver(true, "test-client-id", "test-client-secret").resolve(37.5172, 127.0473);

        assertThat(region.code()).isEqualTo("36110");
        assertThat(region.sido()).isEqualTo("세종특별자치시");
        assertThat(region.sigungu()).isEqualTo("세종특별자치시");
        assertThat(region.regionName()).isEqualTo("세종특별자치시");
        server.verify();
    }

    @Test
    void 네이버_내부_상태가_결과없음이면_지역없음으로_처리한다() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":3,\"name\":\"no results\"},\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    @Test
    void 네이버_내부_실패_상태는_외부_조회_실패로_처리한다() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":100,\"name\":\"invalid request\"},\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED, HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void 인증_정보가_누락되면_외부_요청없이_사용불가로_처리한다() {
        assertFailure(resolver(true, "test-client-id", null),
                MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    private NaverPlaceAdministrativeRegionResolver resolver(boolean enabled, String clientId, String clientSecret) {
        return new NaverPlaceAdministrativeRegionResolver(
                builder.build(),
                new NaverLocalRegionProperties(enabled, clientId, clientSecret, "https://naver.test", null, null, null, null)
        );
    }

    private String successResponse(String codeId, String sido, String sigungu) {
        return """
                {
                  "status": {"code": 0, "name": "ok"},
                  "results": [{
                    "name": "legalcode",
                    "code": {"id": "%s", "mappingId": "ignored"},
                    "region": {
                      "area1": {"name": "%s"},
                      "area2": {"name": "%s"}
                    }
                  }]
                }
                """.formatted(codeId, sido, sigungu);
    }

    private void assertFailure(
            NaverPlaceAdministrativeRegionResolver resolver,
            MapErrorCode errorCode,
            HttpStatus status
    ) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(errorCode);
                    assertThat(exception.getStatus()).isEqualTo(status);
                });
    }
}
