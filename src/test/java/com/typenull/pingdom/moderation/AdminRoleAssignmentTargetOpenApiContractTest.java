package com.typenull.pingdom.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminRoleAssignmentTargetOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminOpenApiDocumentsRoleAssignmentTargetSearch() throws Exception {
        JsonNode api = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs/admin"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode operation = api.path("paths")
                .path("/admin/users/role-targets")
                .path("get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.path("security").get(0).path("bearerAuth").isArray()).isTrue();
        assertThat(responseSchemaRef(operation, "200"))
                .isEqualTo("#/components/schemas/AdminRoleAssignmentTargetSearchResponse");
        assertThat(responseSchemaRef(operation, "401"))
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(responseSchemaRef(operation, "403"))
                .isEqualTo("#/components/schemas/ErrorResponse");
    }

    private String responseSchemaRef(JsonNode operation, String responseCode) {
        JsonNode content = operation.path("responses").path(responseCode).path("content");
        JsonNode schema = content.path("application/json").path("schema");
        if (schema.isMissingNode()) {
            schema = content.path("*/*").path("schema");
        }
        return schema.path("$ref").asText();
    }
}
