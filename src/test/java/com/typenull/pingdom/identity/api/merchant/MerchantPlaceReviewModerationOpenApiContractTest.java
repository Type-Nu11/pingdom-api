package com.typenull.pingdom.identity.api.merchant;

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
class MerchantPlaceReviewModerationOpenApiContractTest {

    private static final String REVIEW_LIST_PATH = "/merchant-owner/places/{placeId}/reviews";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesMerchantReviewManagementListOnlyInMerchantOpenApiDocument() throws Exception {
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode operation = merchantDocument.at("/paths/~1merchant-owner~1places~1{placeId}~1reviews/get");

        assertThat(merchantDocument.path("paths").has(REVIEW_LIST_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(REVIEW_LIST_PATH)).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantPlaceReviewPageResponse");
        assertThat(merchantDocument.at("/components/schemas/MerchantPlaceReviewPageResponse/properties").toString())
                .contains("reviews", "page", "limit", "totalElements", "totalPages", "hasNext");
        assertThat(merchantDocument.at("/components/schemas/MerchantPlaceReviewResponse/properties/visibilityStatus").toString())
                .contains("VISIBLE", "HIDDEN", "DELETED");
        assertThat(merchantDocument.at("/components/schemas/MerchantPlaceReviewDeletionRequestStatusResponse/nullable").asBoolean())
                .isTrue();
        assertThat(merchantDocument.at("/components/schemas/MerchantPlaceReviewDeletionRequestStatusResponse/properties/status").toString())
                .contains("PENDING", "APPROVED", "REJECTED");
    }

    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
