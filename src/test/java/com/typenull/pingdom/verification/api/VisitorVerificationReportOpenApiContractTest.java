package com.typenull.pingdom.verification.api;

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
class VisitorVerificationReportOpenApiContractTest {

    private static final String REPORT_PATH = "/visitor-verification-reports";
    private static final String REPORT_DETAIL_PATH = "/visitor-verification-reports/{reportId}";
    private static final String CORRECTION_PATH = "/visitor-verification-reports/{reportId}/corrections";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 작성자 제보·정정 경로가 app에 있고 admin에는 없는지 확인한다.
     * 성공/오류 schema와 예시, JWT 선언 및 응답 required/nullable 계약을 검사한다.
     */
    @Test
    void exposeVisitorReportContract() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has(REPORT_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(REPORT_DETAIL_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(CORRECTION_PATH)).isTrue();
        assertThat(webDocument.path("paths").has(REPORT_PATH)).isFalse();
        assertThat(webDocument.path("paths").has(REPORT_DETAIL_PATH)).isFalse();
        assertThat(webDocument.path("paths").has(CORRECTION_PATH)).isFalse();

        JsonNode reportOperations = appDocument.path("paths").path(REPORT_PATH);
        JsonNode listOperation = reportOperations.path("get");
        JsonNode submitOperation = reportOperations.path("post");
        JsonNode detailOperation = appDocument.path("paths").path(REPORT_DETAIL_PATH).path("get");
        JsonNode correctionOperations = appDocument.path("paths").path(CORRECTION_PATH);
        JsonNode listCorrectionsOperation = correctionOperations.path("get");
        JsonNode submitCorrectionOperation = correctionOperations.path("post");

        assertSuccessResponse(listOperation, "200", "MyVisitorVerificationReportPageResponse");
        assertSuccessResponse(submitOperation, "201", "MyVisitorVerificationReportResponse");
        assertSuccessResponse(detailOperation, "200", "MyVisitorVerificationReportResponse");
        assertSuccessResponse(listCorrectionsOperation, "200", "MyVisitorVerificationReportCorrectionPageResponse");
        assertSuccessResponse(submitCorrectionOperation, "201", "MyVisitorVerificationReportCorrectionResponse");

        for (JsonNode operation : List.of(
                listOperation,
                submitOperation,
                detailOperation,
                listCorrectionsOperation,
                submitCorrectionOperation
        )) {
            assertBearerSecurity(operation);
            assertErrorResponse(operation, "401", "INVALID_TOKEN");
            assertErrorResponse(operation, "401", "EXPIRED_TOKEN");
        }
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(appDocument.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");

        assertValidationErrorResponse(listOperation, "400", "VALIDATION_FAILED");
        assertErrorResponse(listOperation, "403", "TOURIST_ACCOUNT_REQUIRED");

        assertValidationOrDomainErrorResponse(submitOperation, "400", "INVALID_REPORT_DETAILS");
        assertErrorResponse(submitOperation, "403", "TOURIST_ACCOUNT_REQUIRED");
        assertErrorResponse(submitOperation, "404", "PLACE_NOT_FOUND");
        assertErrorResponse(submitOperation, "409", "ACTIVE_REPORT_ALREADY_EXISTS");

        assertErrorResponse(detailOperation, "403", "REPORT_FORBIDDEN");
        assertErrorResponse(detailOperation, "404", "REPORT_NOT_FOUND");

        assertValidationErrorResponse(listCorrectionsOperation, "400", "VALIDATION_FAILED");
        assertErrorResponse(listCorrectionsOperation, "403", "CORRECTION_FORBIDDEN");
        assertErrorResponse(listCorrectionsOperation, "404", "REPORT_NOT_FOUND");

        assertValidationOrDomainErrorResponse(
                submitCorrectionOperation,
                "400",
                "INVALID_CORRECTION_DETAILS"
        );
        assertErrorResponse(submitCorrectionOperation, "403", "REPORT_FORBIDDEN");
        assertErrorResponse(submitCorrectionOperation, "404", "REPORT_NOT_FOUND");
        assertErrorResponse(submitCorrectionOperation, "409", "CORRECTION_NOT_ALLOWED");
        assertErrorResponse(submitCorrectionOperation, "409", "ACTIVE_CORRECTION_ALREADY_EXISTS");
        assertThat(submitCorrectionOperation.path("description").asText()).contains("ACCEPTED", "REJECTED");

        assertResponseSchema(
                appDocument,
                "MyVisitorVerificationReportResponse",
                List.of(
                        "id", "placeId", "reportType", "description", "evidenceUrl", "waitTimeMinutes",
                        "languageCode", "couponUsageStatus", "crowdLevel", "status", "rejectionReason",
                        "createdAt", "reviewedAt", "updatedAt"
                ),
                List.of(
                        "evidenceUrl", "waitTimeMinutes", "languageCode", "couponUsageStatus", "crowdLevel",
                        "rejectionReason", "reviewedAt"
                )
        );
        assertResponseSchema(
                appDocument,
                "MyVisitorVerificationReportPageResponse",
                List.of("reports", "page", "limit", "totalElements", "totalPages", "hasNext"),
                List.of()
        );
        assertResponseSchema(
                appDocument,
                "MyVisitorVerificationReportCorrectionResponse",
                List.of(
                        "id", "reportId", "placeId", "reportType", "description", "evidenceUrl",
                        "waitTimeMinutes", "languageCode", "couponUsageStatus", "crowdLevel", "reportStatus",
                        "status", "rejectionReason", "createdAt", "reviewedAt", "updatedAt"
                ),
                List.of(
                        "evidenceUrl", "waitTimeMinutes", "languageCode", "couponUsageStatus", "crowdLevel",
                        "rejectionReason", "reviewedAt"
                )
        );
        assertResponseSchema(
                appDocument,
                "MyVisitorVerificationReportCorrectionPageResponse",
                List.of("corrections", "page", "limit", "totalElements", "totalPages", "hasNext"),
                List.of()
        );
    }

    /** 성공 응답이 지정한 components schema를 참조하는지 확인한다. */
    private void assertSuccessResponse(JsonNode operation, String responseCode, String schemaName) {
        assertThat(operation.at("/responses/" + responseCode + "/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/" + schemaName);
    }

    /** operation에 bearerAuth security 배열이 선언되어 있는지 확인한다. */
    private void assertBearerSecurity(JsonNode operation) {
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    /** 지정 HTTP 응답의 ErrorResponse 참조와 예시의 실제 오류 코드를 대조한다. */
    private void assertErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);

        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
    }

    /** validation 오류 응답의 schema와 예시 코드를 함께 대조한다. */
    private void assertValidationErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);

        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ValidationErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
    }

    /** 같은 HTTP 응답에서 validation·도메인 오류가 oneOf로 표현되고 각 예시가 존재하는지 확인한다. */
    private void assertValidationOrDomainErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode schema = operation.at("/responses/" + responseCode + "/content/*~1*/schema");

        assertThat(schema.path("oneOf").toString()).contains("ValidationErrorResponse", "ErrorResponse");
        assertThat(operation.at("/responses/" + responseCode + "/content/*~1*/examples/VALIDATION_FAILED/value/code")
                .asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(operation.at("/responses/" + responseCode + "/content/*~1*/examples/" + errorCode + "/value/code")
                .asText()).isEqualTo(errorCode);
    }

    /** required 필드 목록이 정확히 일치하고 지정된 필드들이 null을 허용하는지 확인한다. */
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

    /** MockMvc로 지정 문서를 조회해 HTTP 200을 요구하고 UTF-8 JSON 트리로 파싱한다. */
    private JsonNode readApiDocs(String path) throws Exception {
        String body = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }
}
