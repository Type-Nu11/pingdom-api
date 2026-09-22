package com.typenull.pingdom.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
class MyPlaceReviewOpenApiContractTest {

    private static final String MY_REVIEWS_PATH = "/users/me/reviews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 내 리뷰 경로가 app 문서에만 포함되고 Bearer 인증, 페이지 필수 필드, 리뷰 노출 상태 enum이 문서화되는지 확인한다. */
    @Test
    void documentsMyReviewContract() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode commonDocument = readApiDocs("/v3/api-docs/common");
        JsonNode operation = appDocument.at("/paths/~1users~1me~1reviews/get");

        assertThat(appDocument.path("paths").has(MY_REVIEWS_PATH)).isTrue();
        assertThat(commonDocument.path("paths").has(MY_REVIEWS_PATH)).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MyPlaceReviewPageResponse");
        assertThat(appDocument.at("/components/schemas/MyPlaceReviewPageResponse/required").toString())
                .contains("reviews", "page", "limit", "totalElements", "totalPages", "hasNext");
        assertThat(appDocument.at("/components/schemas/MyPlaceReviewResponse/properties/visibilityStatus").toString())
                .contains("VISIBLE", "HIDDEN", "DELETED");
    }

    /** 지정 그룹 OpenAPI의 HTTP 200 응답을 확인하고 UTF-8 본문을 JSON으로 변환한다. */
    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
