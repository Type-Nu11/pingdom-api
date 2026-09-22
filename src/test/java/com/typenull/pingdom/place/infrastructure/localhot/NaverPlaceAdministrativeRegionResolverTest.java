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

    /** 네이버 요청과 응답을 대역 서버로 격리해 외부 API 없이 변환 계약을 확인. */
    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    /** 경도·위도 쿼리와 인증 헤더를 확인하고 legalcode의 10자리 코드와 시·도/시·군·구를 기존 5자리 지역 계약으로 변환하는지 확인. */
    @Test
    void mapsNaverLegalRegion() {
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

    /** 세종의 빈 area2를 세종특별자치시로 보완하며 최종 지역명을 중복하지 않는지 확인. */
    @Test
    void normalizesSejongWithoutDistrict() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess(successResponse("3611010700", "세종특별자치시", ""), MediaType.APPLICATION_JSON));

        var region = resolver(true, "test-client-id", "test-client-secret").resolve(37.5172, 127.0473);

        assertThat(region.code()).isEqualTo("36110");
        assertThat(region.sido()).isEqualTo("세종특별자치시");
        assertThat(region.sigungu()).isEqualTo("세종특별자치시");
        assertThat(region.regionName()).isEqualTo("세종특별자치시");
        server.verify();
    }

    /** 서울 응답에 area2가 비어 있으면 세종 예외를 적용하지 않고 지역 없음·404로 처리하는지 확인. */
    @Test
    void rejectsMissingNonSejongDistrict() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess(successResponse("1168010100", "서울특별시", ""), MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    /** 9자리로 잘린 법정동 코드를 유효한 지역으로 쓰지 않고 지역 없음으로 처리하는지 확인. */
    @Test
    void rejectsIncompleteLegalCode() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("""
                        {"status":{"code":0},"results":[{
                          "name":"legalcode",
                          "code":{"id":"361101070"},
                          "region":{"area1":{"name":"세종특별자치시"},"area2":{"name":""}}
                        }]}""", MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    /** addr 결과만 존재하면 legalcode로 대체하지 않고 지역 없음으로 처리하는지 확인. */
    @Test
    void requiresLegalCodeResult() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("""
                        {"status":{"code":0},"results":[{
                          "name":"addr",
                          "code":{"id":"1168010100"},
                          "region":{"area1":{"name":"서울특별시"},"area2":{"name":"강남구"}}
                        }]}""", MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    /** HTTP 성공이어도 네이버 내부 코드 3은 지역 없음·404로 변환하는지 확인. */
    @Test
    void mapsNaverNoResultsStatus() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":3,\"name\":\"no results\"},\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    /** HTTP 성공 본문의 내부 오류 코드 100은 외부 조회 실패·502로 변환하는지 확인. */
    @Test
    void mapsNaverFailureStatus() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{\"status\":{\"code\":100,\"name\":\"invalid request\"},\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-client-id", "test-client-secret"),
                MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED, HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    /** 정상 조회 후 같은 좌표를 요청하면 저장된 지역을 재사용해 HTTP 요청이 한 번만 발생하는지 확인. */
    @Test
    void reusesCachedRegionResult() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess(successResponse("1168010100", "서울특별시", "강남구"), MediaType.APPLICATION_JSON));

        var resolver = resolver(true, "test-client-id", "test-client-secret");
        assertThat(resolver.resolve(37.5172, 127.0473).code()).isEqualTo("11680");
        assertThat(resolver.resolve(37.5172, 127.0473).code()).isEqualTo("11680");

        server.verify();
    }

    /** 비밀키가 없으면 HTTP 요청 전에 지역 조회 사용 불가·503으로 거부하는지 확인. */
    @Test
    void rejectsMissingNaverCredentials() {
        assertFailure(resolver(true, "test-client-id", null),
                MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    /** 활성 상태·인증 정보를 지정한 resolver를 현재 HTTP 대역 client에 연결. */
    private NaverPlaceAdministrativeRegionResolver resolver(boolean enabled, String clientId, String clientSecret) {
        return new NaverPlaceAdministrativeRegionResolver(
                builder.build(),
                new NaverLocalRegionProperties(enabled, clientId, clientSecret, "https://naver.test", null, null, null, null)
        );
    }

    /** 전달한 법정동 코드와 행정구역명을 정상 응답 JSON에 채워 변환 경계를 구성. */
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

    /** 같은 좌표 조회에서 기대하는 도메인 오류 코드와 HTTP 상태를 함께 검사. */
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
