package com.typenull.pingdom.integration.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "pingdom.openapi.public-access.enabled=true")
@AutoConfigureMockMvc
class PlaceReviewManagementOpenApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void requiresAuthenticationOnPatchAndDelete() throws Exception {
        mvc.perform(patch("/places/1/reviews/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(delete("/places/1/reviews/1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void publishesPatchDeleteBodySecurityAndErrorsOnlyInCommon() throws Exception {
        var common = mapper.readTree(mvc.perform(get("/v3/api-docs/common")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var app = mapper.readTree(mvc.perform(get("/v3/api-docs/app")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String path = "/places/{placeId}/reviews/{reviewId}";
        var patch = common.path("paths").path(path).path("patch");
        var delete = common.path("paths").path(path).path("delete");
        assertThat(patch.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(patch.at("/requestBody/content/application~1json/schema/$ref").asText()).endsWith("PlaceReviewUpdateRequest");
        for (String response : java.util.List.of("200", "400", "401", "403", "404", "409")) {
            assertThat(patch.path("responses").has(response)).as(response).isTrue();
        }
        assertThat(delete.path("responses").has("204")).isTrue();
        assertThat(delete.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(app.path("paths").has(path)).isFalse();
        assertThat(common.at("/components/schemas/PlaceReviewUpdateRequest/properties/reviewMediaIds/maxItems").asInt()).isEqualTo(3);
    }
}
