package com.typenull.pingdom.moderation;

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
class CommunityModerationOpenApiContractTest {

    private static final String POST_REPORT_PATH = "/community/posts/{postId}/reports";
    private static final String COMMENT_REPORT_PATH = "/community/posts/{postId}/comments/{commentId}/reports";
    private static final String ADMIN_REPORT_PATH = "/admin/community-reports";
    private static final String ADMIN_REPORT_DETAIL_PATH = "/admin/community-reports/{reportId}";
    private static final String ADMIN_REPORT_ACCEPT_PATH = "/admin/community-reports/{reportId}/accept";
    private static final String ADMIN_REPORT_DECLINE_PATH = "/admin/community-reports/{reportId}/decline";
    private static final String ADMIN_POST_DETAIL_PATH = "/admin/community/posts/{postId}";
    private static final String ADMIN_COMMENT_LIST_PATH = "/admin/community/posts/{postId}/comments";
    private static final String ADMIN_COMMENT_DETAIL_PATH = "/admin/community/posts/{postId}/comments/{commentId}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void 신고와_관리자_처리_API의_요청_응답_오류_계약을_문서화한다() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has(POST_REPORT_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(COMMENT_REPORT_PATH)).isTrue();
        assertThat(appDocument.path("paths").has(ADMIN_REPORT_PATH)).isFalse();
        assertThat(adminDocument.path("paths").has(ADMIN_REPORT_PATH)).isTrue();
        assertThat(adminDocument.path("paths").has(ADMIN_POST_DETAIL_PATH)).isTrue();
        assertThat(adminDocument.path("paths").has(POST_REPORT_PATH)).isFalse();

        JsonNode postReport = operation(appDocument, POST_REPORT_PATH, "post");
        JsonNode commentReport = operation(appDocument, COMMENT_REPORT_PATH, "post");
        JsonNode adminReportList = operation(adminDocument, ADMIN_REPORT_PATH, "get");
        JsonNode adminReportDetail = operation(adminDocument, ADMIN_REPORT_DETAIL_PATH, "get");
        JsonNode accept = operation(adminDocument, ADMIN_REPORT_ACCEPT_PATH, "post");
        JsonNode decline = operation(adminDocument, ADMIN_REPORT_DECLINE_PATH, "post");

        assertSuccessResponse(postReport, "201", "CommunityReportCreateResponse");
        assertSuccessResponse(commentReport, "201", "CommunityReportCreateResponse");
        assertSuccessResponse(adminReportList, "200", "AdminCommunityReportPageResponse");
        assertSuccessResponse(adminReportDetail, "200", "AdminCommunityReportResponse");
        assertSuccessResponse(accept, "200", "AdminCommunityReportActionResponse");
        assertSuccessResponse(decline, "200", "AdminCommunityReportActionResponse");

        for (JsonNode operation : List.of(postReport, commentReport)) {
            assertBearerSecurity(operation);
            assertErrorResponse(operation, "401");
            assertErrorResponse(operation, "404");
            assertErrorResponse(operation, "409");
            assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/CommunityReportCreateRequest");
        }
        for (JsonNode operation : List.of(adminReportList, adminReportDetail, accept, decline)) {
            assertBearerSecurity(operation);
            assertErrorResponse(operation, "401");
            assertErrorResponse(operation, "403");
        }
        for (JsonNode operation : List.of(adminReportDetail, accept, decline)) {
            assertErrorResponse(operation, "404");
        }
        for (JsonNode operation : List.of(accept, decline)) {
            assertErrorResponse(operation, "409");
        }
        for (String path : List.of(ADMIN_POST_DETAIL_PATH, ADMIN_COMMENT_LIST_PATH, ADMIN_COMMENT_DETAIL_PATH)) {
            assertErrorResponse(operation(adminDocument, path, "get"), "404");
        }
    }

    @Test
    void 관리자_커뮤니티_목록_항목은_각각_고유한_OpenAPI_스키마를_사용한다() throws Exception {
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");

        assertItemSchema(adminDocument, "AdminCommunityPostPageResponse", "posts", "AdminCommunityPostItem", "postId", "title");
        assertItemSchema(adminDocument, "AdminCommunityCommentPageResponse", "comments", "AdminCommunityCommentItem", "commentId", "content");
        assertItemSchema(adminDocument, "AdminCommunityReportPageResponse", "reports", "AdminCommunityReportItem", "reportId", "targetType", "status");
    }

    @Test
    void 신고_상세의_원문_ID는_필수이며_대상_유형별_예시를_제공한다() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs/admin");
        JsonNode schema = document.at("/components/schemas/AdminCommunityReportResponse");
        assertThat(schema.path("required")).contains(objectMapper.getNodeFactory().textNode("postId"));
        assertThat(schema.at("/properties/postId/type").asText()).isEqualTo("integer");
        assertThat(schema.at("/properties/postId/nullable").asBoolean(false)).isFalse();
        JsonNode examples = operation(document, ADMIN_REPORT_DETAIL_PATH, "get")
                .at("/responses/200/content/*~1*/examples");
        JsonNode post = examples.path("POST").path("value");
        JsonNode comment = examples.path("COMMENT").path("value");
        assertThat(post.path("targetType").asText()).isEqualTo("POST");
        assertThat(post.path("postId").asLong()).isEqualTo(101L);
        assertThat(post.path("targetId")).isEqualTo(post.path("postId"));
        assertThat(comment.path("targetType").asText()).isEqualTo("COMMENT");
        assertThat(comment.path("postId").asLong()).isEqualTo(101L);
        assertThat(comment.path("targetId").asLong()).isEqualTo(202L);
        assertThat(document.at("/components/schemas/AdminCommunityReportItem/properties").has("postId")).isFalse();
        assertThat(document.at("/components/schemas/AdminCommunityReportActionResponse/properties").has("postId")).isFalse();
    }

    private JsonNode operation(JsonNode document, String path, String method) {
        return document.path("paths").path(path).path(method);
    }

    private void assertSuccessResponse(JsonNode operation, String responseCode, String schemaName) {
        assertThat(operation.at("/responses/" + responseCode + "/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/" + schemaName);
    }

    private void assertBearerSecurity(JsonNode operation) {
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    private void assertErrorResponse(JsonNode operation, String responseCode) {
        JsonNode content = operation.path("responses").path(responseCode).path("content");
        JsonNode schema = content.path("*/*").path("schema");
        if (schema.isMissingNode()) {
            schema = content.path("application/json").path("schema");
        }

        assertThat(schema.path("$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
    }

    private void assertItemSchema(
            JsonNode document,
            String pageSchemaName,
            String listField,
            String itemSchemaName,
            String... expectedFields
    ) {
        JsonNode itemSchema = document.at("/components/schemas/" + pageSchemaName + "/properties/" + listField + "/items");
        assertThat(itemSchema.path("$ref").asText()).isEqualTo("#/components/schemas/" + itemSchemaName);
        for (String expectedField : expectedFields) {
            assertThat(document.at("/components/schemas/" + itemSchemaName + "/properties/" + expectedField).isMissingNode())
                    .isFalse();
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
