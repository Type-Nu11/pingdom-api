package com.typenull.pingdom.verification.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ScoutProfileOpenApiContractTest {

    private static final String SCOUT_PROFILE_PATH = "/users/me/scout-profile";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesScoutProfileContractInTheCorrectApiGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has(SCOUT_PROFILE_PATH)).isTrue();
        assertThat(webDocument.path("paths").has(SCOUT_PROFILE_PATH)).isFalse();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/scout-profiles/{scoutUserId}/eligibility/grant"))
                .isTrue();

        JsonNode profileOperations = appDocument.path("paths").path(SCOUT_PROFILE_PATH);
        JsonNode getOperation = profileOperations.path("get");
        JsonNode postOperation = profileOperations.path("post");
        JsonNode putOperation = profileOperations.path("put");

        JsonNode responseSchema = appDocument.at(
                "/paths/~1users~1me~1scout-profile/get/responses/200/content/*~1*/schema/$ref"
        );
        assertThat(responseSchema.asText()).isEqualTo("#/components/schemas/ScoutProfileResponse");
        assertThat(appDocument.at("/components/schemas/ScoutProfileResponse/properties/profileStatus").toString())
                .contains("PENDING", "ACTIVE", "SUSPENDED", "REVOKED");
        assertThat(appDocument.at(
                "/components/schemas/ScoutProfileResponse/properties/activityEligibilityStatus"
        ).toString()).contains("PENDING", "ELIGIBLE", "SUSPENDED", "EXPIRED", "REVOKED");

        assertBearerSecurity(getOperation);
        assertBearerSecurity(postOperation);
        assertBearerSecurity(putOperation);
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");

        assertErrorResponse(getOperation, "404", "SCOUT_PROFILE_NOT_FOUND");
        assertErrorResponse(postOperation, "403", "SCOUT_PROFILE_ACCOUNT_REQUIRED");
        assertErrorResponse(postOperation, "409", "SCOUT_PROFILE_ALREADY_EXISTS");
        assertErrorResponse(putOperation, "403", "SCOUT_PROFILE_ACCOUNT_REQUIRED");
        assertErrorResponse(putOperation, "404", "SCOUT_PROFILE_NOT_FOUND");
        assertErrorResponse(putOperation, "409", "INVALID_SCOUT_PROFILE_STATE");
        assertThat(getOperation.path("description").asText()).contains("SCOUT_PROFILE_NOT_FOUND");
        assertThat(postOperation.path("description").asText())
                .contains("PENDING", "ACTIVE", "SUSPENDED", "REVOKED");
        assertThat(putOperation.path("description").asText())
                .contains("PENDING", "ACTIVE", "SUSPENDED", "REVOKED", "활동 자격 상태");

        JsonNode scoutProfileResponse = appDocument.path("components").path("schemas").path("ScoutProfileResponse");
        assertThat(scoutProfileResponse.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "userId",
                        "displayName",
                        "introduction",
                        "profileStatus",
                        "profileReviewedByAdminUserId",
                        "profileReviewedAt",
                        "profileStatusReason",
                        "activityEligibilityStatus",
                        "eligibleFrom",
                        "eligibleUntil",
                        "eligibilityReviewedByAdminUserId",
                        "eligibilityReviewedAt",
                        "eligibilityStatusReason",
                        "createdAt",
                        "updatedAt"
                );
        for (String nullableField : List.of(
                "introduction",
                "profileReviewedByAdminUserId",
                "profileReviewedAt",
                "profileStatusReason",
                "eligibleFrom",
                "eligibleUntil",
                "eligibilityReviewedByAdminUserId",
                "eligibilityReviewedAt",
                "eligibilityStatusReason"
        )) {
            assertThat(scoutProfileResponse.path("properties").path(nullableField).path("nullable").asBoolean())
                    .as("%s 필드는 null을 허용해야 한다", nullableField)
                    .isTrue();
        }

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

    private void assertBearerSecurity(JsonNode operation) {
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    private void assertErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);

        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
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
