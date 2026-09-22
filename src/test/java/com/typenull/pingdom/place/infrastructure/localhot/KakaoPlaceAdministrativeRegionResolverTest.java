package com.typenull.pingdom.place.infrastructure.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoPlaceAdministrativeRegionResolverTest {

    private static final String REQUEST_URL =
            "https://kakao.test/v2/local/geo/coord2regioncode.json?x=127.0473&y=37.5172";
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    /** 실제 네트워크 없이 Kakao 요청 URL·헤더·응답을 제어하는 RestClient 대역을 생성. */
    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    /** 비활성 또는 빈 API key 조합에서는 configured=false와 사용 불가·503을 반환하고 HTTP 요청이 없는지 확인. */
    @ParameterizedTest
    @CsvSource({"false,test-key", "true,''", "true,'   '"})
    void skipsUnavailableKakaoRequests(boolean enabled, String apiKey) {
        KakaoPlaceAdministrativeRegionResolver resolver = resolver(enabled, apiKey);

        assertThat(resolver.isConfigured()).isFalse();
        assertFailure(resolver, MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    /** 경도를 x·위도를 y로 보내고 Kakao 인증 헤더를 적용하며 행정동 대신 법정동 코드 앞 5자리와 지역명을 반환하는지 확인. */
    @Test
    void resolvesKakaoLegalRegion() {
        server.expect(requestTo(REQUEST_URL))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess("""
                        {"documents":[
                          {"region_type":"H","code":"9999999999"},
                          {"region_type":"B","code":"1168010100",
                           "region_1depth_name":"서울특별시","region_2depth_name":"강남구"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var region = resolver(true, "test-key").resolve(37.5172, 127.0473);

        assertThat(region.code()).isEqualTo("11680");
        assertThat(region.regionName()).isEqualTo("서울특별시 강남구");
        server.verify();
    }

    /** Kakao의 인증 실패·제한 초과·서버 오류 응답을 동일한 외부 조회 실패·502 계약으로 변환하는지 확인. */
    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {
            "UNAUTHORIZED", "FORBIDDEN", "TOO_MANY_REQUESTS", "INTERNAL_SERVER_ERROR"
    })
    void providerErrorsRemainBadGateway(HttpStatus status) {
        server.expect(requestTo(REQUEST_URL)).andRespond(withStatus(status));

        assertFailure(resolver(true, "test-key"), MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED,
                HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    /** HTTP 대역에서 발생한 SocketTimeoutException을 외부 조회 실패·502로 변환하는지 확인. */
    @Test
    void timeoutRemainsBadGateway() {
        server.expect(requestTo(REQUEST_URL)).andRespond(withException(new SocketTimeoutException("timeout")));

        assertFailure(resolver(true, "test-key"), MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED,
                HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    /** 빈 Kakao documents 응답을 지역 없음·404로 변환하는지 확인. */
    @Test
    void missingLegalRegionReturnsNotFound() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-key"), MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    /** 활성 여부와 API key를 바꿀 수 있는 Kakao resolver를 HTTP 대역에 연결. */
    private KakaoPlaceAdministrativeRegionResolver resolver(boolean enabled, String apiKey) {
        return new KakaoPlaceAdministrativeRegionResolver(builder.build(),
                new KakaoLocalRegionProperties(enabled, apiKey, "https://kakao.test", null, null, null, null));
    }

    /** 고정 좌표 조회의 도메인 코드와 HTTP 상태를 함께 비교. */
    private void assertFailure(KakaoPlaceAdministrativeRegionResolver resolver, MapErrorCode code, HttpStatus status) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(code);
                    assertThat(exception.getStatus()).isEqualTo(status);
                });
    }
}
