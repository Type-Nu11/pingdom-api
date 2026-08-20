package com.typenull.pingdom.verification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ScoutProfileOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesScoutProfileContractInTheCorrectApiGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/users/me/scout-profile")).isTrue();
        assertThat(webDocument.path("paths").has("/users/me/scout-profile")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/eligibility/grant"))
                .isTrue();

        JsonNode responseSchema = appDocument.at(
                "/paths/~1users~1me~1scout-profile/get/responses/200/content/*~1*/schema/$ref"
        );
        assertThat(responseSchema.asText()).isEqualTo("#/components/schemas/ScoutProfileResponse");
        assertThat(appDocument.at("/components/schemas/ScoutProfileResponse/properties/profileStatus").toString())
                .contains("PENDING", "ACTIVE", "SUSPENDED", "REVOKED");
        assertThat(appDocument.at(
                "/components/schemas/ScoutProfileResponse/properties/activityEligibilityStatus"
        ).toString()).contains("PENDING", "ELIGIBLE", "SUSPENDED", "EXPIRED", "REVOKED");

        assertThat(appDocument.at("/paths/~1users~1me~1scout-profile/post").path("security").isArray())
                .isTrue();
        assertThat(appDocument.at("/paths/~1users~1me~1scout-profile/put").path("security").isArray())
                .isTrue();
        assertThat(webDocument.at("/paths/~1admin~1scout-profiles/get").path("security").isArray())
                .isTrue();

        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/approve")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/suspend")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/revoke")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/eligibility/suspend"))
                .isTrue();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/eligibility/revoke"))
                .isTrue();

        JsonNode profileRequest = appDocument.at("/components/schemas/ScoutProfileRequest");
        assertThat(profileRequest.path("required").toString()).contains("displayName");
        assertThat(profileRequest.at("/properties/displayName/maxLength").asInt()).isEqualTo(100);
        assertThat(profileRequest.at("/properties/introduction/maxLength").asInt()).isEqualTo(1000);

        JsonNode eligibilityRequest = webDocument.at(
                "/components/schemas/ScoutActivityEligibilityGrantRequest"
        );
        assertThat(eligibilityRequest.path("required").toString()).contains("eligibleFrom");
        assertThat(eligibilityRequest.at("/properties/reason/maxLength").asInt()).isEqualTo(500);

        JsonNode listOperation = webDocument.at("/paths/~1admin~1scout-profiles/get");
        assertThat(listOperation.path("parameters").toString()).contains("page", "limit", "status");
    }

    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }
}
