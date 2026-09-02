package com.typenull.pingdom.availability.api;

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
class AvailabilityOpenApiContractTest {

    private static final List<String> AVAILABILITY_REQUIRED_FIELDS = List.of(
            "id",
            "placeId",
            "productId",
            "productType",
            "productName",
            "startsAt",
            "endsAt",
            "totalCapacity",
            "remainingCapacity",
            "status"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void reservableProductCreateRequestExposesOnlyTicketAndClass() throws Exception {
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");
        JsonNode requestSchema = merchantDocument.at(
                "/components/schemas/ReservableProductCreateRequest"
        );
        JsonNode productTypeSchema = resolveSchema(
                merchantDocument,
                requestSchema.at("/properties/productType")
        );

        assertThat(textValues(productTypeSchema.path("enum")))
                .containsExactly("TICKET", "CLASS");
        assertThat(textValues(requestSchema.path("required")))
                .contains("placeId", "productType", "name");
    }

    @Test
    void availabilityResponseDeclaresRequiredAndNullableProductSummaryInAppAndMerchantContracts()
            throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        assertAvailabilityResponseContract(appDocument);
        assertAvailabilityResponseContract(merchantDocument);
    }

    private void assertAvailabilityResponseContract(JsonNode document) {
        JsonNode schema = document.at("/components/schemas/AvailabilityResponse");

        assertThat(textValues(schema.path("required")))
                .containsExactlyInAnyOrderElementsOf(AVAILABILITY_REQUIRED_FIELDS);
        assertThat(schema.at("/properties/productId/nullable").asBoolean()).isTrue();
        assertThat(schema.at("/properties/productName/nullable").asBoolean()).isTrue();
        assertThat(schema.at("/properties/productName/type").asText()).isEqualTo("string");
        assertThat(schema.at("/properties/productName/description").asText())
                .contains("GENERAL", "TICKET/CLASS");
        assertThat(textValues(schema.at("/properties/productType/enum")))
                .containsExactly("GENERAL", "TICKET", "CLASS");
    }

    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        String reference = schema.path("$ref").asText();
        return reference.isBlank() ? schema : document.at(reference.substring(1));
    }

    private List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
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
