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

    /**
     * merchant OpenAPI의 상품 생성 스키마가 TICKET·CLASS만 열거하고 장소·유형·이름을 필수로 표시하는지 검증.
     * 등록할 수 없는 GENERAL 유형이 API 문서에 노출되는 회귀를 방지.
     */
    @Test
    void documentsSupportedProductTypes() throws Exception {
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

    /**
     * app과 merchant OpenAPI 양쪽에 동일한 예약 슬롯 응답의 필수 필드·상품 null 허용 계약이 적용되는지 검증.
     */
    @Test
    void documentsAvailabilityAcrossGroups()
            throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        assertAvailabilityResponseContract(appDocument);
        assertAvailabilityResponseContract(merchantDocument);
    }

    /**
     * 예약 슬롯의 필수 필드 전체와 상품 ID·이름의 nullable 여부를 확인.
     * 상품명 타입·유형별 설명·GENERAL/TICKET/CLASS 열거값까지 점검해 두 API 그룹의 문서 계약을 고정.
     */
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
