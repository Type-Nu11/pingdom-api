package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Naver Geocoding 요청 형식과 외부 오류 변환을 검증합니다. */
class NaverAddressSearchClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    /** 실제 Naver API 대신 요청 URL·헤더·응답 상태를 검증할 mock HTTP 서버를 구성합니다. */
    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    /**
     * 주소 검색 요청이 Naver Geocoding 경로와 서버 전용 인증 헤더를 사용하고, 정상 응답의 addresses 배열만 반환하는지 검증한다.
     */
    @Test
    void sendsGeocodingRequest() {
        server.expect(requestTo("https://naver.test/map-geocode/v2/geocode?query=%EB%B6%84%EB%8B%B9%EA%B5%AC%20%EB%B6%88%EC%A0%95%EB%A1%9C%206&page=1&count=10"))
                .andExpect(header("x-ncp-apigw-api-key-id", "test-client-id"))
                .andExpect(header("x-ncp-apigw-api-key", "test-client-secret"))
                .andRespond(withSuccess("""
                        {"status":"OK","addresses":[{"x":"127.1054328","y":"37.3595963"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client(true, "test-client-id", "test-client-secret").search("분당구 불정로 6")).isNotEmpty();
        server.verify();
    }

    /**
     * 외부 API가 HTTP 429를 반환하면 일반 외부 장애와 구분되는 호출 한도 초과 오류로 변환하는지 검증한다.
     */
    @Test
    void mapsRateLimitError() {
        server.expect(requestTo("https://naver.test/map-geocode/v2/geocode?query=%EB%B6%84%EB%8B%B9%EA%B5%AC%20%EB%B6%88%EC%A0%95%EB%A1%9C%206&page=1&count=10"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertFailure(client(true, "test-client-id", "test-client-secret"), MapErrorCode.NAVER_ADDRESS_SEARCH_RATE_LIMITED);
        server.verify();
    }

    /**
     * Geocoding 설정이 비활성화되었거나 비밀 인증값이 없으면 외부 요청을 보내지 않고 사용 불가 오류를 반환하는지 검증한다.
     */
    @Test
    void rejectsMissingCredentials() {
        assertFailure(client(true, "test-client-id", null), MapErrorCode.NAVER_ADDRESS_SEARCH_UNAVAILABLE);
        server.verify();
    }

    /** 테스트별 활성 상태와 인증값으로 주소 검색 클라이언트를 생성합니다. */
    private NaverAddressSearchClient client(boolean enabled, String clientId, String clientSecret) {
        return new NaverAddressSearchClient(
                builder.build(),
                new NaverAddressSearchClient.Properties(enabled, clientId, clientSecret, "https://naver.test", null, null)
        );
    }

    /** 외부 호출 실패가 기대한 공통 지도 오류 코드로 변환되는지 검증합니다. */
    private void assertFailure(NaverAddressSearchClient client, MapErrorCode errorCode) {
        assertThatThrownBy(() -> client.search("분당구 불정로 6"))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
