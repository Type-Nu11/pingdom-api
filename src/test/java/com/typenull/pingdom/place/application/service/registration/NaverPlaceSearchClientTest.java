package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverPlaceSearchClientTest {

    private static final String REQUEST_URL = "https://naver.test/search/v1/local"
            + "?query=%ED%95%91%EB%8D%A4%20%EC%B9%B4%ED%8E%98&display=5&format=json";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl("https://naver.test");
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendsApiHubLocalSearchRequest() {
        server.expect(requestTo(REQUEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-NCP-APIGW-API-KEY-ID", "test-client-id"))
                .andExpect(header("X-NCP-APIGW-API-KEY", "test-client-secret"))
                .andExpect(headerDoesNotExist("X-Naver-Client-Id"))
                .andExpect(headerDoesNotExist("X-Naver-Client-Secret"))
                .andRespond(withSuccess("""
                        {"items":[{"title":"핑덤 카페"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client(true, "test-client-id", "test-client-secret").search("핑덤 카페"))
                .hasSize(1);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "text/plain;charset=UTF-8", "application/json"})
    void parsesUtf8JsonRegardlessOfContentType(String contentType) {
        byte[] body = "{\"items\":[{\"title\":\"롯데월드\",\"address\":\"서울 송파구\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo(REQUEST_URL))
                .andRespond(withSuccess(body, MediaType.parseMediaType(contentType)));

        var items = client(true, "test-client-id", "test-client-secret").search("핑덤 카페");

        assertThat(items.get(0).path("title").asText()).isEqualTo("롯데월드");
        assertThat(items.get(0).path("address").asText()).isEqualTo("서울 송파구");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-json", "{", "null"})
    void mapsInvalidPlainTextResponseToSearchFailure(String body) {
        server.expect(requestTo(REQUEST_URL)).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));

        assertFailure(client(true, "test-client-id", "test-client-secret"), MapErrorCode.NAVER_PLACE_SEARCH_FAILED);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource(value = {"false,id,secret", "true,NULL,secret", "true,id,NULL", "true,' ',secret"},
            nullValues = "NULL")
    void rejectsUnconfiguredSearchWithoutCallingProvider(boolean enabled, String clientId, String clientSecret) {
        assertFailure(client(enabled, clientId, clientSecret), MapErrorCode.NAVER_PLACE_SEARCH_UNAVAILABLE);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 503})
    void preservesProviderFailureMapping(int status) {
        server.expect(requestTo(REQUEST_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertFailure(client(true, "test-client-id", "test-client-secret"), MapErrorCode.NAVER_PLACE_SEARCH_FAILED);
        server.verify();
    }

    @Test
    void rejectsEmptyResponse() {
        server.expect(requestTo(REQUEST_URL)).andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertFailure(client(true, "test-client-id", "test-client-secret"), MapErrorCode.NAVER_PLACE_SEARCH_FAILED);
        server.verify();
    }

    @Test
    void defaultsToApiHubAndPreservesTimeouts() {
        var properties = new NaverPlaceSearchClient.Properties(true, " id ", " secret ", null, null, null);

        assertThat(properties.baseUrl()).isEqualTo("https://naverapihub.apigw.ntruss.com");
        assertThat(properties.clientId()).isEqualTo("id");
        assertThat(properties.clientSecret()).isEqualTo("secret");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    private NaverPlaceSearchClient client(boolean enabled, String clientId, String clientSecret) {
        return new NaverPlaceSearchClient(builder.build(), new NaverPlaceSearchClient.Properties(
                enabled, clientId, clientSecret, "https://naver.test", null, null));
    }

    private void assertFailure(NaverPlaceSearchClient client, MapErrorCode errorCode) {
        assertThatThrownBy(() -> client.search("핑덤 카페"))
                .isInstanceOfSatisfying(MapException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
