package com.typenull.pingdom.place.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
class NearbyReservablePlaceOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesNearbyReservablePlaceContractInAppOpenApi() throws Exception {
        JsonNode document = readApiDocs();
        JsonNode operation = document.at("/paths/~1places~1nearby-reservable/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(parameter(operation, "latitude").path("required").asBoolean()).isTrue();
        assertThat(parameter(operation, "longitude").path("required").asBoolean()).isTrue();
        assertThat(textValues(parameter(operation, "sort").at("/schema/enum")))
                .containsExactly("NEAREST", "EARLIEST_AVAILABLE", "POPULAR");
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/NearbyReservablePlaceResponse");

        assertThat(textValues(document.at("/components/schemas/NearbyReservablePlaceResponse/required")))
                .containsExactlyInAnyOrder(
                        "places", "page", "limit", "totalElements", "totalPages", "hasNext", "queriedAt");
        JsonNode item = document.at("/components/schemas/NearbyReservablePlaceItem");
        assertThat(textValues(item.path("required"))).contains("placeId", "availabilityId", "productId", "productName");
        assertThat(item.at("/properties/productId/nullable").asBoolean()).isTrue();
        assertThat(item.at("/properties/productName/nullable").asBoolean()).isTrue();
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) return parameter;
        }
        return objectMapper.missingNode();
    }

    private List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
    }

    private JsonNode readApiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs/app"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
