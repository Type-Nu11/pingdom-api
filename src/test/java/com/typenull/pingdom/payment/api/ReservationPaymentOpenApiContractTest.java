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

    @Test
    void exposesReservationAndPaymentContractsInTheAppGroup() throws Exception {
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

    private void assertSuccessResponse(JsonNode operation, String schemaName) {
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/" + schemaName);
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

    private void assertValidationErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);

        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ValidationErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
    }

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

    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
