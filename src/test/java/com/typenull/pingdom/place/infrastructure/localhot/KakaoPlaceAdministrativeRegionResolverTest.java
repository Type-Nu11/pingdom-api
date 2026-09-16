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

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://kakao.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @ParameterizedTest
    @CsvSource({"false,test-key", "true,''", "true,'   '"})
    void disabledOrMissingKeyReturnsUnavailableWithoutExternalRequest(boolean enabled, String apiKey) {
        KakaoPlaceAdministrativeRegionResolver resolver = resolver(enabled, apiKey);

        assertThat(resolver.isConfigured()).isFalse();
        assertFailure(resolver, MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE);
        server.verify();
    }

    @Test
    void resolvesLegalRegionUsingLongitudeAsXAndLatitudeAsY() {
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

    @Test
    void timeoutRemainsBadGateway() {
        server.expect(requestTo(REQUEST_URL)).andRespond(withException(new SocketTimeoutException("timeout")));

        assertFailure(resolver(true, "test-key"), MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED,
                HttpStatus.BAD_GATEWAY);
        server.verify();
    }

    @Test
    void missingLegalRegionReturnsNotFound() {
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess("{\"documents\":[]}", MediaType.APPLICATION_JSON));

        assertFailure(resolver(true, "test-key"), MapErrorCode.LOCAL_HOT_REGION_NOT_FOUND, HttpStatus.NOT_FOUND);
        server.verify();
    }

    private KakaoPlaceAdministrativeRegionResolver resolver(boolean enabled, String apiKey) {
        return new KakaoPlaceAdministrativeRegionResolver(builder.build(),
                new KakaoLocalRegionProperties(enabled, apiKey, "https://kakao.test", null, null, null, null));
    }

    private void assertFailure(KakaoPlaceAdministrativeRegionResolver resolver, MapErrorCode code, HttpStatus status) {
        assertThatThrownBy(() -> resolver.resolve(37.5172, 127.0473))
                .isInstanceOfSatisfying(MapException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(code);
                    assertThat(exception.getStatus()).isEqualTo(status);
                });
    }
}
