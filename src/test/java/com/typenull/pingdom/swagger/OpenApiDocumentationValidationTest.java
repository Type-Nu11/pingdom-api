package com.typenull.pingdom.swagger;

import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingTimeRangeRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateResponse;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteItem;
import com.typenull.pingdom.place.api.dto.place.create.PlaceCreateResponse;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.api.dto.place.upload.PlaceUploadRequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OpenApiDocumentationValidationTest {

    private static final List<String> API_DOC_PATHS = List.of(
            "/v3/api-docs",
            "/v3/api-docs/app",
            "/v3/api-docs/common",
            "/v3/api-docs/web"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void documentedExamplesMatchDeclaredSchemas() throws Exception {
        List<String> mismatches = new ArrayList<>();

        for (String apiDocPath : API_DOC_PATHS) {
            JsonNode document = readApiDocs(apiDocPath);
            JsonNode paths = document.path("paths");

            for (Iterator<Entry<String, JsonNode>> pathIterator = paths.fields(); pathIterator.hasNext(); ) {
                Entry<String, JsonNode> pathEntry = pathIterator.next();

                for (Iterator<Entry<String, JsonNode>> operationIterator = pathEntry.getValue().fields(); operationIterator.hasNext(); ) {
                    Entry<String, JsonNode> operationEntry = operationIterator.next();
                    JsonNode operation = operationEntry.getValue();
                    String locationPrefix = apiDocPath + " " + operationEntry.getKey().toUpperCase() + " " + pathEntry.getKey();

                    validateContentExamples(
                            document,
                            operation.path("requestBody").path("content"),
                            locationPrefix + " requestBody",
                            mismatches
                    );

                    JsonNode responses = operation.path("responses");
                    for (Iterator<Entry<String, JsonNode>> responseIterator = responses.fields(); responseIterator.hasNext(); ) {
                        Entry<String, JsonNode> responseEntry = responseIterator.next();
                        validateContentExamples(
                                document,
                                responseEntry.getValue().path("content"),
                                locationPrefix + " response " + responseEntry.getKey(),
                                mismatches
                        );
                    }
                }
            }
        }

        assertThat(mismatches).isEmpty();
    }

    @Test
    void apiDocsExposeJwtBearerSecurityScheme() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");
        JsonNode bearerAuth = document.path("components").path("securitySchemes").path("bearerAuth");

        assertThat(bearerAuth.path("type").asText()).isEqualTo("http");
        assertThat(bearerAuth.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerAuth.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    @Test
    void deprecatedEndpointsAreMarkedInApiDocs() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertThat(appDocument.at("/paths/~1firebase~1fcm-token/patch/deprecated").asBoolean()).isTrue();
        assertThat(appDocument.path("paths").has("/place")).isFalse();
        assertThat(appDocument.path("paths").has("/users/bookmarks")).isFalse();
    }

    @Test
    void travelPurposePreferenceApiIsExposedInAppGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertThat(appDocument.path("paths").has("/users/me/travel-purposes")).isTrue();
        assertThat(appDocument.at("/paths/~1users~1me~1travel-purposes/get/responses/200/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/TravelPurposePreferenceResponse");
        assertThat(appDocument.at("/paths/~1users~1me~1travel-purposes/put/requestBody/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/TravelPurposePreferenceUpdateRequest");
        boolean travelPurposesRequired = false;
        for (JsonNode requiredField : appDocument.path("components").path("schemas")
                .path("TravelPurposePreferenceUpdateRequest").path("required")) {
            if ("travelPurposes".equals(requiredField.asText())) {
                travelPurposesRequired = true;
                break;
            }
        }
        assertThat(travelPurposesRequired).isTrue();
    }

    @Test
    void travelScheduleAndCurrentActivityIntentApisAreExposedInAppGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertThat(appDocument.path("paths").has("/users/me/travel-schedules")).isTrue();
        assertThat(appDocument.path("paths").has("/users/me/travel-schedules/{scheduleId}")).isTrue();
        assertThat(appDocument.path("paths").has("/users/me/travel-schedules/{scheduleId}/cancel")).isTrue();
        assertThat(appDocument.path("paths").has("/users/me/current-activity-intent")).isTrue();
        assertThat(appDocument.at("/paths/~1users~1me~1travel-schedules/post/requestBody/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/TravelScheduleCreateRequest");
        assertThat(appDocument.at("/paths/~1users~1me~1current-activity-intent/put/requestBody/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/CurrentActivityIntentUpdateRequest");
        assertThat(appDocument.at("/paths/~1users~1me~1current-activity-intent/get/responses/200/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/CurrentActivityIntentResponse");
    }

    @Test
    void merchantOwnerApisAreSeparatedIntoAppAndWebGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

        assertThat(appDocument.path("paths").has("/users/me/merchant-owner-profile")).isTrue();
        assertThat(appDocument.path("paths").has("/merchant-owner/me")).isTrue();
        assertThat(appDocument.path("paths").has("/admin/merchant-owners")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/merchant-owners")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-owners/{userId}/approve")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("MerchantOwnerProfileResponse")).isTrue();
    }

    @Test
    void touristInformationSchemasDeclareNullableStringFields() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");

        for (String schemaName : List.of(
                "PlaceUploadRequest",
                "PlaceCreateResponse",
                "PlaceDetailResponse",
                "PlaceListItem",
                "AdminMapPlaceTouristInfoUpdateRequest",
                "AdminMapPlaceTouristInfoUpdateResponse",
                "AdminMapPlaceDetailResponse",
                "AdminMapPlaceItem"
        )) {
            assertNullableProperty(document, schemaName, "englishName");
            assertNullableProperty(document, schemaName, "touristSummary");
        }
        assertNullableProperty(document, "PlaceAutocompleteItem", "englishName");
    }

    @Test
    void operatingScheduleSchemasExposeRegularHoursAndDateExceptions() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");

        assertThat(document.path("paths").has("/admin/places/{id}/operating-schedule")).isTrue();
        assertThat(document.path("components").path("schemas").path("PlaceDetailResponse")
                .path("properties").path("regularHours").path("type").asText()).isEqualTo("array");
        assertThat(document.path("components").path("schemas").path("PlaceDetailResponse")
                .path("properties").path("operatingExceptions").path("type").asText()).isEqualTo("array");
        assertThat(document.path("components").path("schemas").path("AdminMapPlaceOperatingScheduleUpdateRequest")
                .path("properties").path("regularHours").path("type").asText()).isEqualTo("array");
        assertThat(document.path("components").path("schemas").path("PlaceOperatingExceptionResponse")
                .path("properties").path("closed").path("type").asText()).isEqualTo("boolean");
        assertThat(document.path("components").path("schemas").path("AdminMapPlaceOperatingTimeRangeRequest")
                .path("properties").path("opensAt").path("type").asText()).isEqualTo("string");
        assertThat(document.path("components").path("schemas").path("AdminMapPlaceOperatingTimeRangeRequest")
                .path("properties").path("opensAt").path("format").asText()).isEqualTo("time");
        assertThat(document.path("components").path("schemas").path("PlaceOperatingTimeRangeResponse")
                .path("properties").path("opensAt").path("type").asText()).isEqualTo("string");
        assertThat(document.path("components").path("schemas").path("PlaceOperatingTimeRangeResponse")
                .path("properties").path("opensAt").path("format").asText()).isEqualTo("time");
        assertThat(document.at("/paths/~1admin~1places~1{id}~1operating-schedule/patch/responses/400/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(document.at("/paths/~1admin~1places~1{id}~1operating-schedule/patch/responses/404/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ErrorResponse");
    }

    @Test
    void periodEventSchemasExposePublicAndAdminContracts() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");

        assertThat(document.path("paths").has("/events")).isTrue();
        assertThat(document.path("paths").has("/events/{eventId}")).isTrue();
        assertThat(document.path("paths").has("/admin/place-events")).isTrue();
        assertThat(document.path("paths").has("/admin/place-events/{eventId}/publish")).isTrue();
        assertThat(document.path("components").path("schemas").path("PlaceEventListResponse")
                .path("properties").path("events").path("type").asText()).isEqualTo("array");
        assertThat(document.path("components").path("schemas").path("PlaceEventDetailResponse")
                .path("properties").path("scheduleStatus").path("type").asText()).isEqualTo("string");
        assertThat(document.path("components").path("schemas").path("AdminPlaceEventRequest")
                .path("required")).hasSize(6);
        assertThat(document.at("/paths/~1events~1{eventId}/get/responses/404/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ErrorResponse");
        for (String responsePath : List.of(
                "/paths/~1admin~1place-events/post/responses/400",
                "/paths/~1admin~1place-events/post/responses/404",
                "/paths/~1admin~1place-events~1{eventId}/patch/responses/400",
                "/paths/~1admin~1place-events~1{eventId}/patch/responses/404",
                "/paths/~1admin~1place-events~1{eventId}/patch/responses/409",
                "/paths/~1admin~1place-events~1{eventId}~1publish/post/responses/404",
                "/paths/~1admin~1place-events~1{eventId}~1publish/post/responses/409",
                "/paths/~1admin~1place-events~1{eventId}~1cancel/post/responses/404",
                "/paths/~1admin~1place-events~1{eventId}~1cancel/post/responses/409"
        )) {
            assertThat(document.at(responsePath + "/content/*~1*/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/ErrorResponse");
        }
    }

    @Test
    void periodEventApiGroupsSeparatePublicAndAdminPaths() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

        assertThat(appDocument.path("paths").has("/events")).isTrue();
        assertThat(appDocument.path("paths").has("/admin/place-events")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/place-events")).isTrue();
    }

    @Test
    void notificationSettingSchemasExposeQuietHoursAsTimeStrings() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");

        for (String schemaName : List.of("NotificationSettingUpdateRequest", "NotificationSettingResponse")) {
            assertThat(document.path("components").path("schemas").path(schemaName)
                    .path("properties").path("quietHoursStart").path("type").asText()).isEqualTo("string");
            assertThat(document.path("components").path("schemas").path(schemaName)
                    .path("properties").path("quietHoursStart").path("format").asText()).isEqualTo("time");
            assertThat(document.path("components").path("schemas").path(schemaName)
                    .path("properties").path("quietHoursEnd").path("type").asText()).isEqualTo("string");
            assertThat(document.path("components").path("schemas").path(schemaName)
                    .path("properties").path("quietHoursEnd").path("format").asText()).isEqualTo("time");
        }
    }

    @Test
    void resolveSchemaFollowsNestedRefsAndUnescapesJsonPointer() throws Exception {
        JsonNode document = objectMapper.readTree("""
                {
                  "components": {
                    "schemas": {
                      "Wrapper": {
                        "$ref": "#/components/schemas/Nested~1Schema"
                      },
                      "Nested/Schema": {
                        "$ref": "#/components/schemas/Actual~0Value"
                      },
                      "Actual~Value": {
                        "type": "object",
                        "required": ["name"],
                        "properties": {
                          "name": {
                            "type": "string"
                          }
                        }
                      }
                    }
                  }
                }
                """);

        JsonNode resolved = resolveSchema(document, document.at("/components/schemas/Wrapper"));

        assertThat(resolved.path("type").asText()).isEqualTo("object");
        assertThat(resolved.path("required")).hasSize(1);
        assertThat(resolved.path("properties").path("name").path("type").asText()).isEqualTo("string");
    }

    private JsonNode readApiDocs(String apiDocPath) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(apiDocPath))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertNullableProperty(JsonNode document, String schemaName, String propertyName) {
        JsonNode property = document.path("components")
                .path("schemas")
                .path(schemaName)
                .path("properties")
                .path(propertyName);

        assertThat(property.isMissingNode())
                .as("%s.%s property must exist", schemaName, propertyName)
                .isFalse();
        assertThat(property.path("nullable").asBoolean())
                .as("%s.%s must allow null", schemaName, propertyName)
                .isTrue();
    }

    private void validateContentExamples(
            JsonNode document,
            JsonNode content,
            String location,
            List<String> mismatches
    ) {
        if (content.isMissingNode()) {
            return;
        }

        for (Iterator<Entry<String, JsonNode>> contentIterator = content.fields(); contentIterator.hasNext(); ) {
            Entry<String, JsonNode> contentEntry = contentIterator.next();
            JsonNode mediaType = contentEntry.getValue();
            JsonNode schema = mediaType.path("schema");

            if (mediaType.has("example")) {
                validateAgainstSchema(
                        document,
                        schema,
                        mediaType.get("example"),
                        location + " " + contentEntry.getKey() + " example",
                        mismatches
                );
            }

            JsonNode examples = mediaType.path("examples");
            for (Iterator<Entry<String, JsonNode>> exampleIterator = examples.fields(); exampleIterator.hasNext(); ) {
                Entry<String, JsonNode> exampleEntry = exampleIterator.next();
                JsonNode value = exampleEntry.getValue().get("value");
                if (value != null) {
                    validateAgainstSchema(
                            document,
                            schema,
                            value,
                            location + " " + contentEntry.getKey() + " examples." + exampleEntry.getKey(),
                            mismatches
                    );
                }
            }
        }
    }

    private void validateAgainstSchema(
            JsonNode document,
            JsonNode schema,
            JsonNode value,
            String location,
            List<String> mismatches
    ) {
        JsonNode resolvedSchema = resolveSchema(document, schema);
        if (resolvedSchema.isMissingNode() || resolvedSchema.isNull() || value == null || value.isNull()) {
            return;
        }

        if (resolvedSchema.has("enum") && !resolvedSchema.path("enum").isEmpty()) {
            boolean matches = false;
            for (JsonNode enumValue : resolvedSchema.path("enum")) {
                if (enumValue.equals(value)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                mismatches.add(location + " enum mismatch: " + value);
            }
        }

        if (resolvedSchema.has("allOf")) {
            for (JsonNode item : resolvedSchema.path("allOf")) {
                validateAgainstSchema(document, item, value, location, mismatches);
            }
        }

        if (resolvedSchema.has("oneOf")) {
            boolean anyMatched = false;
            for (JsonNode item : resolvedSchema.path("oneOf")) {
                List<String> candidateMismatches = new ArrayList<>();
                validateAgainstSchema(document, item, value, location, candidateMismatches);
                if (candidateMismatches.isEmpty()) {
                    anyMatched = true;
                    break;
                }
            }
            if (!anyMatched) {
                mismatches.add(location + " does not match any oneOf schema");
            }
            return;
        }

        String type = resolvedSchema.path("type").asText();
        if (type.isBlank()) {
            if (resolvedSchema.has("properties") || resolvedSchema.has("required")) {
                type = "object";
            } else if (resolvedSchema.has("items")) {
                type = "array";
            } else {
                return;
            }
        }

        switch (type) {
            case "object" -> validateObject(document, resolvedSchema, value, location, mismatches);
            case "array" -> validateArray(document, resolvedSchema, value, location, mismatches);
            case "string" -> {
                if (!value.isTextual()) {
                    mismatches.add(location + " expected string but was " + value.getNodeType());
                }
            }
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    mismatches.add(location + " expected integer but was " + value.getNodeType());
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    mismatches.add(location + " expected number but was " + value.getNodeType());
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) {
                    mismatches.add(location + " expected boolean but was " + value.getNodeType());
                }
            }
            default -> {
            }
        }
    }

    private void validateObject(
            JsonNode document,
            JsonNode schema,
            JsonNode value,
            String location,
            List<String> mismatches
    ) {
        if (!value.isObject()) {
            mismatches.add(location + " expected object but was " + value.getNodeType());
            return;
        }

        for (JsonNode requiredField : schema.path("required")) {
            String fieldName = requiredField.asText();
            if (!value.has(fieldName)) {
                mismatches.add(location + " missing required field: " + fieldName);
            }
        }

        JsonNode properties = schema.path("properties");
        for (Iterator<Entry<String, JsonNode>> propertyIterator = properties.fields(); propertyIterator.hasNext(); ) {
            Entry<String, JsonNode> propertyEntry = propertyIterator.next();
            JsonNode propertyValue = value.get(propertyEntry.getKey());
            if (propertyValue != null && !propertyValue.isNull()) {
                validateAgainstSchema(
                        document,
                        propertyEntry.getValue(),
                        propertyValue,
                        location + "." + propertyEntry.getKey(),
                        mismatches
                );
            }
        }
    }

    private void validateArray(
            JsonNode document,
            JsonNode schema,
            JsonNode value,
            String location,
            List<String> mismatches
    ) {
        if (!value.isArray()) {
            mismatches.add(location + " expected array but was " + value.getNodeType());
            return;
        }

        for (int index = 0; index < value.size(); index++) {
            validateAgainstSchema(
                    document,
                    schema.path("items"),
                    value.get(index),
                    location + "[" + index + "]",
                    mismatches
            );
        }
    }

    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        if (schema == null || schema.isMissingNode()) {
            return objectMapper.createObjectNode();
        }

        JsonNode current = schema;
        while (current.has("$ref")) {
            String refValue = current.path("$ref").asText();
            if (!refValue.startsWith("#/")) {
                break;
            }

            JsonNode next = document;
            for (String segment : refValue.substring(2).split("/")) {
                String unescapedSegment = segment.replace("~1", "/").replace("~0", "~");
                next = next.path(unescapedSegment);
            }

            if (next.isMissingNode() || next == current) {
                break;
            }
            current = next;
        }

        return current;
    }
}
