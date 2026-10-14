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
            "startsAt",
            "endsAt",
            "totalCapacity",
            "remainingCapacity",
            "status",
            "conditionsVersion",
            "reservationTerms"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 종료된 예약 상품 경로와 상품 입력 필드가 OpenAPI에 노출되지 않는지 검증. */
    @Test
    void removesReservableProductContract() throws Exception {
        JsonNode merchant = readApiDocs("/v3/api-docs/merchant");
        assertThat(merchant.path("paths").has("/merchant-owner/reservable-products")).isFalse();
        assertThat(merchant.path("paths").has("/merchant-owner/reservable-products/{productId}/activate")).isFalse();
        assertThat(merchant.path("paths").has("/merchant-owner/reservable-products/{productId}/deactivate")).isFalse();
        var input = merchant.at("/components/schemas/AvailabilityUpsertRequest/properties");
        assertThat(input.has("productId")).isFalse();
        assertThat(input.has("productType")).isFalse();
    }

    /**
     * app과 merchant OpenAPI 양쪽에 동일한 예약 슬롯 응답의 필수 필드·취소 정책 계약이 적용되는지 검증.
     */
    @Test
    void documentsAvailabilityAcrossGroups()
            throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        assertAvailabilityResponseContract(appDocument);
        assertAvailabilityResponseContract(merchantDocument);
    }

    /** 예약 슬롯에 상품·금액 필드가 없고 시간대·취소 조건만 제공되는지 검증. */
    private void assertAvailabilityResponseContract(JsonNode document) {
        JsonNode schema = document.at("/components/schemas/AvailabilityResponse");

        assertThat(textValues(schema.path("required")))
                .containsExactlyInAnyOrderElementsOf(AVAILABILITY_REQUIRED_FIELDS);
        assertThat(schema.path("properties").has("productId")).isFalse();
        assertThat(schema.path("properties").has("productName")).isFalse();
        assertThat(schema.path("properties").has("productType")).isFalse();
        assertThat(schema.at("/properties/reservationTerms/nullable").asBoolean()).isTrue();
        JsonNode termsSchema = resolveSchema(document, schema.at("/properties/reservationTerms"));
        assertThat(textValues(termsSchema.path("required"))).containsExactlyInAnyOrder(
                "timezone", "cancellable",
                "cancellationCutoffMinutes");
        assertThat(termsSchema.path("properties").has("unitAmountMinor")).isFalse();
        assertThat(termsSchema.path("properties").has("additionalAmountMinor")).isFalse();
        assertThat(termsSchema.path("properties").has("currency")).isFalse();
        assertThat(termsSchema.at("/properties/cancellationCutoffMinutes/nullable").asBoolean()).isTrue();
    }

    /**
     * 인라인 스키마는 그대로 사용하고 $ref가 있으면 같은 OpenAPI 문서의 참조 위치를 해석.
     */
    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        String reference = schema.path("$ref").asText();
        return reference.isBlank() ? schema : document.at(reference.substring(1));
    }

    /**
     * JSON 배열 요소를 순서대로 문자열 목록으로 변환해 required와 enum의 목록 assertion에 사용.
     */
    private List<String> textValues(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        arrayNode.forEach(node -> values.add(node.asText()));
        return values;
    }

    /**
     * 주어진 OpenAPI 경로가 200을 반환하는지 확인한 뒤 UTF-8 응답을 JSON 트리로 읽음.
     */
    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
