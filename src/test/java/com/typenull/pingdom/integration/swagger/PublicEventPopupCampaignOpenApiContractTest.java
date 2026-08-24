package com.typenull.pingdom.integration.swagger;

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
class PublicEventPopupCampaignOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesPublicEventAndPopupCampaignContractsWithoutLeakingMerchantResponses() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        assertThat(appDocument.path("paths").has("/events")).isTrue();
        assertThat(appDocument.path("paths").has("/events/{eventId}")).isTrue();
        assertThat(appDocument.path("paths").has("/popup-campaigns")).isTrue();
        assertThat(appDocument.path("paths").has("/popup-campaigns/{campaignId}")).isTrue();

        JsonNode eventList = appDocument.at("/paths/~1events/get");
        JsonNode eventDetail = appDocument.at("/paths/~1events~1{eventId}/get");
        JsonNode campaignList = appDocument.at("/paths/~1popup-campaigns/get");
        JsonNode campaignDetail = appDocument.at("/paths/~1popup-campaigns~1{campaignId}/get");

        assertBearerSecurity(eventList);
        assertBearerSecurity(eventDetail);
        assertBearerSecurity(campaignList);
        assertBearerSecurity(campaignDetail);

        assertThat(eventList.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PlaceEventListResponse");
        assertThat(eventDetail.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PlaceEventDetailResponse");
        assertThat(campaignList.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PublicPopupCampaignPageResponse");
        assertThat(campaignDetail.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PublicPopupCampaignResponse");

        assertParameterRange(eventList, "page", 1, 10_000);
        assertParameterRange(eventList, "limit", 1, 100);
        assertParameterRange(campaignList, "page", 1, 10_000);
        assertParameterRange(campaignList, "limit", 1, 100);
        assertThat(parameter(eventList, "fromAt").path("example").asText()).isEqualTo("2026-08-01T00:00:00Z");
        assertThat(parameter(eventList, "toAt").path("example").asText()).isEqualTo("2026-09-01T00:00:00Z");

        assertErrorResponse(eventList, "400", "PLACE_EVENT_SEARCH_CONDITION_INVALID");
        assertErrorResponse(eventDetail, "404", "PLACE_EVENT_NOT_FOUND");
        assertErrorResponse(campaignList, "400", "INVALID_INPUT");
        assertErrorResponse(campaignDetail, "404", "CAMPAIGN_NOT_FOUND");

        assertRequiredFields(appDocument, "PlaceEventListResponse", List.of(
                "events", "page", "limit", "totalCount", "totalPages", "hasNext"
        ));
        assertRequiredFields(appDocument, "PlaceEventListItem", List.of(
                "id", "placeId", "placeName", "title", "eventType", "startAt", "endAt", "scheduleStatus"
        ));
        assertRequiredFields(appDocument, "PlaceEventDetailResponse", List.of(
                "id", "placeId", "placeName", "placeAddress", "title", "description", "eventType", "startAt", "endAt", "scheduleStatus"
        ));
        assertRequiredFields(appDocument, "PublicPopupCampaignPageResponse", List.of(
                "items", "page", "limit", "totalCount", "totalPages", "hasNext"
        ));
        assertRequiredFields(appDocument, "PublicPopupCampaignResponse", List.of(
                "id", "brandId", "brandName", "brandLogoUrl", "placeId", "title", "description", "startsAt", "endsAt", "status", "createdAt", "updatedAt"
        ));

        assertNullableProperty(appDocument, "PlaceEventDetailResponse", "description");
        assertNullableProperty(appDocument, "PublicPopupCampaignResponse", "brandLogoUrl");
        assertUtcDateTimeProperty(appDocument, "PlaceEventListItem", "startAt");
        assertUtcDateTimeProperty(appDocument, "PlaceEventListItem", "endAt");
        assertUtcDateTimeProperty(appDocument, "PublicPopupCampaignResponse", "startsAt");
        assertUtcDateTimeProperty(appDocument, "PublicPopupCampaignResponse", "endsAt");
        assertUtcDateTimeProperty(appDocument, "PublicPopupCampaignResponse", "createdAt");
        assertUtcDateTimeProperty(appDocument, "PublicPopupCampaignResponse", "updatedAt");
        assertThat(appDocument.at("/components/schemas/PlaceEventListItem/properties/scheduleStatus").toString())
                .contains("UPCOMING", "ONGOING")
                .doesNotContain("ENDED");
        assertThat(appDocument.at("/components/schemas/PublicPopupCampaignResponse/properties/status").toString())
                .contains("PUBLISHED")
                .doesNotContain("DRAFT", "CLOSED");

        assertThat(merchantDocument.path("components").path("schemas").has("PopupCampaignPageResponse")).isTrue();
        assertThat(merchantDocument.path("components").path("schemas").has("PublicPopupCampaignPageResponse")).isFalse();
        assertThat(merchantDocument.at("/components/schemas/PopupCampaignPageResponse/properties/totalElements").isMissingNode())
                .isFalse();
    }

    private void assertBearerSecurity(JsonNode operation) {
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
    }

    private void assertParameterRange(JsonNode operation, String parameterName, double minimum, double maximum) {
        JsonNode schema = parameter(operation, parameterName).path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("integer");
        assertThat(schema.path("format").asText()).isEqualTo("int32");
        assertThat(schema.path("minimum").asDouble()).isEqualTo(minimum);
        assertThat(schema.path("maximum").asDouble()).isEqualTo(maximum);
    }

    private void assertErrorResponse(JsonNode operation, String responseCode, String errorCode) {
        JsonNode response = operation.path("responses").path(responseCode);
        assertThat(response.at("/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(response.at("/content/*~1*/examples/" + errorCode + "/value/code").asText())
                .isEqualTo(errorCode);
    }

    private void assertRequiredFields(JsonNode document, String schemaName, List<String> expectedFields) {
        assertThat(document.at("/components/schemas/" + schemaName + "/required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(expectedFields);
    }

    private void assertNullableProperty(JsonNode document, String schemaName, String propertyName) {
        assertThat(document.at("/components/schemas/" + schemaName + "/properties/" + propertyName + "/nullable").asBoolean())
                .isTrue();
    }

    private void assertUtcDateTimeProperty(JsonNode document, String schemaName, String propertyName) {
        JsonNode property = document.at("/components/schemas/" + schemaName + "/properties/" + propertyName);
        assertThat(property.path("format").asText()).isEqualTo("date-time");
        assertThat(property.path("example").asText()).endsWith("Z");
    }

    private JsonNode parameter(JsonNode operation, String parameterName) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameterName.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return objectMapper.missingNode();
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
