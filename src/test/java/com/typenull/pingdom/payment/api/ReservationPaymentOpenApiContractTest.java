package com.typenull.pingdom.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@Tag("integration")
@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ReservationPaymentOpenApiContractTest {

    private static final String RESERVATION_DETAIL_PATH = "/reservations/{reservationId}";
    private static final String PAYMENTS_PATH = "/payments";
    private static final String PAYMENT_DETAIL_PATH = "/payments/{paymentId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 예약 상세·결제 목록/상세가 app에만 노출되고 성공 스키마·JWT 인증·401/403/404 및 검증 오류 예제가 문서화되는지 확인한다.
     * 응답 필수/nullable 필드·상태 열거값·빈 결제 목록의 404 미노출과 merchant 결제 스키마의 failedAt도 검증한다.
     */
    @Test
    void documentsAppReservationPaymentContracts() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        assertThat(appDocument.path("paths").has(RESERVATION_DETAIL_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(PAYMENTS_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(PAYMENT_DETAIL_PATH)).isTrue();
        assertThat(merchantDocument.path("paths").has(RESERVATION_DETAIL_PATH)).isFalse();
        assertThat(merchantDocument.path("paths").has(PAYMENTS_PATH)).isFalse();
        assertThat(merchantDocument.path("paths").has(PAYMENT_DETAIL_PATH)).isFalse();

        JsonNode reservationDetailOperation = appDocument.path("paths").path(RESERVATION_DETAIL_PATH).path("get");
        JsonNode paymentListOperation = appDocument.path("paths").path(PAYMENTS_PATH).path("get");
        JsonNode paymentDetailOperation = appDocument.path("paths").path(PAYMENT_DETAIL_PATH).path("get");

        assertSuccessResponse(reservationDetailOperation, "ReservationResponse");
        assertSuccessResponse(paymentListOperation, "PaymentPageResponse");
        assertSuccessResponse(paymentDetailOperation, "PaymentResponse");

        for (JsonNode operation : List.of(reservationDetailOperation, paymentListOperation, paymentDetailOperation)) {
            assertBearerSecurity(operation);
            assertErrorResponse(operation, "401", "INVALID_TOKEN");
            assertErrorResponse(operation, "401", "EXPIRED_TOKEN");
        }
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");

        assertErrorResponse(reservationDetailOperation, "403", "TOURIST_ACCOUNT_REQUIRED");
        assertErrorResponse(reservationDetailOperation, "403", "RESERVATION_FORBIDDEN");
        assertErrorResponse(reservationDetailOperation, "404", "RESERVATION_NOT_FOUND");

        assertValidationErrorResponse(paymentListOperation, "400", "VALIDATION_FAILED");
        assertErrorResponse(paymentListOperation, "403", "PAYMENT_FORBIDDEN");
        assertThat(paymentListOperation.path("responses").has("404")).isFalse();
        assertThat(paymentListOperation.path("description").asText()).contains("빈 배열");

        assertErrorResponse(paymentDetailOperation, "403", "PAYMENT_FORBIDDEN");
        assertErrorResponse(paymentDetailOperation, "404", "PAYMENT_NOT_FOUND");

        assertResponseSchema(
                appDocument,
                "ReservationResponse",
                List.of(
                        "id", "touristUserId", "availabilityId", "productId", "productType", "quantity",
                        "status", "createdAt", "confirmedAt", "canceledAt", "updatedAt"
                ),
                List.of("productId", "confirmedAt", "canceledAt")
        );
        assertResponseSchema(
                appDocument,
                "PaymentResponse",
                List.of(
                        "id", "reservationId", "provider", "providerPaymentId", "amountMinor", "currency",
                        "status", "failureCode", "createdAt", "paidAt", "failedAt", "refundedAt"
                ),
                List.of(
                        "providerPaymentId", "amountMinor", "currency", "failureCode", "paidAt", "failedAt",
                        "refundedAt"
                )
        );
        assertResponseSchema(
                appDocument,
                "PaymentPageResponse",
                List.of("payments", "page", "limit", "totalElements", "totalPages", "hasNext"),
                List.of()
        );
        assertThat(appDocument.at("/components/schemas/ReservationResponse/properties/status").toString())
                .contains("PENDING", "CONFIRMED", "CANCELED");
        assertThat(appDocument.at("/components/schemas/PaymentResponse/properties/status").toString())
                .contains("PROCESSING", "PAID", "REFUND_PROCESSING", "FAILED", "REFUNDED");
        assertThat(merchantDocument.path("components").path("schemas").path("PaymentResponse")
                .path("properties").has("failedAt")).isTrue();
    }

    /**
     * 200 응답의 참조 스키마가 지정된 응답 DTO를 가리키는지 확인한다.
     */
    private void assertSuccessResponse(JsonNode operation, String schemaName) {
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/" + schemaName);
    }

    /**
     * operation에 bearerAuth 보안 요구사항 배열이 존재하는지 확인한다.
     */
    private void assertBearerSecurity(JsonNode operation) {
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    /**
     * 지정 HTTP 상태가 ErrorResponse를 참조하고 해당 오류 코드 예제를 포함하는지 확인한다.
     */
    private void assertErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);

        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
    }

    /**
     * 입력 검증 오류가 ValidationErrorResponse와 지정 코드 예제를 사용하도록 문서 계약을 확인한다.
     */
    private void assertValidationErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);

        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ValidationErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
    }

    /**
     * 응답 스키마의 필수 필드가 기대 목록과 정확히 일치하고 지정 필드가 nullable인지 확인한다.
     */
    private void assertResponseSchema(
            JsonNode document,
            String schemaName,
            List<String> requiredFields,
            List<String> nullableFields
    ) {
        JsonNode schema = document.at("/components/schemas/" + schemaName);

        assertThat(schema.path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(requiredFields);
        for (String nullableField : nullableFields) {
            assertThat(schema.path("properties").path(nullableField).path("nullable").asBoolean())
                    .as("%s 필드는 null을 허용해야 한다", nullableField)
                    .isTrue();
        }
    }

    /**
     * OpenAPI 경로의 200 응답을 UTF-8로 읽고 Jackson 트리로 변환해 문서 계약 검증에 제공한다.
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
