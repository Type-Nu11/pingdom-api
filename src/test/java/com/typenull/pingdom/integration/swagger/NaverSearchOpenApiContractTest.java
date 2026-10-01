package com.typenull.pingdom.integration.swagger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** merchant 및 전체 생성 문서에서 서로 다른 검색 후보와 실제 오류 계약을 검증합니다. */
@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NaverSearchOpenApiContractTest {

    private static final String BASE_PATH = "/users/me/merchant-place-applications";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs/merchant", "/v3/api-docs"})
    void preservesDistinctSearchItemFields(String documentPath) throws Exception {
        JsonNode document = readApiDocs(documentPath);
        JsonNode addressItems = items(document, "NaverAddressSearchResponse");
        JsonNode placeItems = items(document, "NaverPlaceSearchResponse");

        assertThat(addressItems.at("/items/$ref").asText()).isEqualTo("#/components/schemas/NaverAddressSearchItem");
        assertThat(placeItems.at("/items/$ref").asText()).isEqualTo("#/components/schemas/NaverPlaceSearchItem");
        assertThat(addressItems.path("maxItems").asInt()).isEqualTo(10);
        assertThat(placeItems.path("maxItems").asInt()).isEqualTo(5);

        JsonNode address = document.at("/components/schemas/NaverAddressSearchItem/properties");
        JsonNode place = document.at("/components/schemas/NaverPlaceSearchItem/properties");
        assertThat(fieldNames(address)).containsExactlyInAnyOrder("roadAddress", "jibunAddress", "postalCode", "latitude", "longitude");
        assertThat(fieldNames(place)).containsExactlyInAnyOrder("name", "roadAddress", "jibunAddress", "latitude", "longitude");
        assertThat(address.at("/postalCode/type").asText()).isEqualTo("string");
        assertThat(address.at("/postalCode/nullable").asBoolean()).isTrue();
        assertThat(place.at("/name/type").asText()).isEqualTo("string");
        for (JsonNode properties : List.of(address, place)) {
            for (String coordinate : List.of("latitude", "longitude")) {
                assertThat(properties.path(coordinate).path("type").asText()).isEqualTo("number");
                assertThat(properties.path(coordinate).path("format").asText()).isEqualTo("double");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs/merchant", "/v3/api-docs"})
    void documentsSuccessAndQueryConstraints(String documentPath) throws Exception {
        JsonNode document = readApiDocs(documentPath);
        assertSuccess(operation(document, "address"), "NaverAddressSearchResponse");
        assertSuccess(operation(document, "place"), "NaverPlaceSearchResponse");

        for (String search : List.of("address", "place")) {
            JsonNode operation = operation(document, search);
            JsonNode query = parameter(operation, "query");
            assertThat(query.path("in").asText()).isEqualTo("query");
            assertThat(query.path("required").asBoolean()).isTrue();
            assertThat(query.at("/schema/type").asText()).isEqualTo("string");
            assertThat(query.at("/schema/minLength").asInt()).isEqualTo(1);
            assertThat(query.at("/schema/maxLength").asInt()).isEqualTo(100);
            assertThat(query.path("description").asText()).contains("trim", "공백만 있는 값은 허용하지 않습니다");
            assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs/merchant", "/v3/api-docs"})
    void documentsOnlyImplementedSearchErrors(String documentPath) throws Exception {
        JsonNode document = readApiDocs(documentPath);
        JsonNode address = operation(document, "address");
        JsonNode place = operation(document, "place");

        assertThat(fieldNames(address.path("responses"))).containsExactlyInAnyOrder("200", "400", "401", "403", "429", "502", "503", "504");
        assertThat(fieldNames(place.path("responses"))).containsExactlyInAnyOrder("200", "400", "401", "403", "502", "503");
        for (MapErrorCode error : List.of(MapErrorCode.NAVER_ADDRESS_SEARCH_RATE_LIMITED,
                MapErrorCode.NAVER_ADDRESS_SEARCH_FAILED, MapErrorCode.NAVER_ADDRESS_SEARCH_UNAVAILABLE,
                MapErrorCode.NAVER_ADDRESS_SEARCH_TIMEOUT)) {
            assertDomainError(address, error);
        }
        for (MapErrorCode error : List.of(MapErrorCode.NAVER_PLACE_SEARCH_FAILED, MapErrorCode.NAVER_PLACE_SEARCH_UNAVAILABLE)) {
            assertDomainError(place, error);
        }
        for (JsonNode operation : List.of(address, place)) {
            assertThat(responseSchema(operation, "400").path("oneOf").findValuesAsText("$ref"))
                    .containsExactlyInAnyOrder("#/components/schemas/ErrorResponse", "#/components/schemas/ValidationErrorResponse");
            assertThat(operation.at("/responses/400/description").asText())
                    .contains("INVALID_REQUEST_PARAMETER", "PLACE_SEARCH_CONDITION_INVALID");
            for (String status : List.of("401", "403")) {
                assertThat(responseSchema(operation, status).path("$ref").asText()).isEqualTo("#/components/schemas/ErrorResponse");
            }
        }
    }

    private void assertDomainError(JsonNode operation, MapErrorCode error) {
        String status = Integer.toString(error.getStatus().value());
        assertThat(operation.path("responses").path(status).path("description").asText()).contains(error.getCode());
        assertThat(responseSchema(operation, status).path("$ref").asText()).isEqualTo("#/components/schemas/ErrorResponse");
    }

    private void assertSuccess(JsonNode operation, String schemaName) {
        assertThat(responseSchema(operation, "200").path("$ref").asText()).isEqualTo("#/components/schemas/" + schemaName);
    }

    private JsonNode responseSchema(JsonNode operation, String status) {
        JsonNode content = operation.path("responses").path(status).path("content");
        return content.has("application/json") ? content.path("application/json").path("schema") : content.path("*/*").path("schema");
    }

    private JsonNode operation(JsonNode document, String search) {
        return document.path("paths").path(BASE_PATH + "/naver-" + search + "-search").path("get");
    }

    private JsonNode items(JsonNode document, String responseName) {
        return document.path("components").path("schemas").path(responseName).path("properties").path("items");
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) return parameter;
        }
        return objectMapper.missingNode();
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
