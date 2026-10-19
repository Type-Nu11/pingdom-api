package com.typenull.pingdom.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = {
        "pingdom.dev-profile.enabled=true",
        "pingdom.openapi.public-access.enabled=true"
})
@AutoConfigureMockMvc
class PlaceReviewSummaryApiIntegrationTest {

    private static final String SUMMARY_PATH = "/places/{placeId}/reviews/summary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 실제 보안 체인이 인증 없는 집계 요청을 컨트롤러 진입 전에 거부하는지 검증합니다. */
    @Test
    void rejectsUnauthenticatedSummaryRequests() throws Exception {
        mockMvc.perform(get("/places/17/reviews/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /** Springdoc가 실제 생성한 그룹·응답·오류·중첩 필드 계약을 검증합니다. */
    @Test
    void generatesCommonSummaryContractWithoutDuplicatingAppPath() throws Exception {
        JsonNode common = apiDocs("/v3/api-docs/common");
        JsonNode app = apiDocs("/v3/api-docs/app");
        JsonNode operation = common.path("paths").path(SUMMARY_PATH).path("get");

        assertThat(common.path("paths").has(SUMMARY_PATH)).isTrue();
        assertThat(app.path("paths").has(SUMMARY_PATH)).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.path("requestBody").isMissingNode()).isTrue();
        assertThat(operation.path("parameters")).hasSize(1);
        JsonNode placeId = operation.path("parameters").get(0);
        assertThat(placeId.path("name").asText()).isEqualTo("placeId");
        assertThat(placeId.path("in").asText()).isEqualTo("path");
        assertThat(placeId.path("required").asBoolean()).isTrue();
        assertThat(placeId.at("/schema/format").asText()).isEqualTo("int64");

        assertThat(responseSchema(operation, "200").path("$ref").asText())
                .isEqualTo("#/components/schemas/PlaceReviewSummaryResponse");
        for (String status : List.of("400", "401", "403", "404", "500")) {
            assertThat(responseSchema(operation, status).path("$ref").asText())
                    .as("%s 오류 응답", status).isEqualTo("#/components/schemas/ErrorResponse");
        }
        assertThat(operation.at("/responses/400/description").asText()).contains("INVALID_REQUEST_PARAMETER");
        assertThat(operation.at("/responses/401/description").asText()).contains("INVALID_TOKEN", "EXPIRED_TOKEN");
        assertThat(operation.at("/responses/404/description").asText()).contains("PLACE_NOT_FOUND");
        assertThat(operation.at("/responses/500/description").asText()).contains("INTERNAL_SERVER_ERROR");

        JsonNode summary = common.at("/components/schemas/PlaceReviewSummaryResponse");
        assertThat(strings(summary.path("required"))).containsExactlyInAnyOrder(
                "placeId", "totalReviewCount", "participantCount", "reasonCounts");
        for (String field : List.of("placeId", "totalReviewCount", "participantCount")) {
            assertThat(summary.path("properties").path(field).path("format").asText()).isEqualTo("int64");
        }
        assertThat(summary.at("/properties/reasonCounts/type").asText()).isEqualTo("array");
        String reasonCountReference = summary.at("/properties/reasonCounts/items/$ref").asText();
        JsonNode reasonCount = common.at(reasonCountReference.substring(1));
        assertThat(strings(reasonCount.path("required"))).containsExactlyInAnyOrder("reason", "count");
        assertThat(reasonCount.at("/properties/count/format").asText()).isEqualTo("int64");
        assertThat(strings(reasonCount.at("/properties/reason/enum"))).containsExactly(
                "FRIENDLY", "EASY_TO_FIND", "GOOD_FOOD", "MULTILINGUAL_SUPPORT", "PARKING", "PHOTO_SPOT", "CLEAN");
    }

    private JsonNode apiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private JsonNode responseSchema(JsonNode operation, String status) {
        JsonNode content = operation.path("responses").path(status).path("content");
        assertThat(content.isObject()).isTrue();
        assertThat(content.size()).isPositive();
        return content.elements().next().path("schema");
    }

    private List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
