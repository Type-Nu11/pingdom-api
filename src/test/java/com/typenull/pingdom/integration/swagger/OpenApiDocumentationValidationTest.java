package com.typenull.pingdom.integration.swagger;

import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingScheduleUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.operating.AdminMapPlaceOperatingTimeRangeRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateRequest;
import com.typenull.pingdom.moderation.api.dto.place.quality.tourist.AdminMapPlaceTouristInfoUpdateResponse;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteItem;
import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingTimeRangeResponse;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
class OpenApiDocumentationValidationTest {

    private static final List<String> API_DOC_PATHS = List.of(
            "/v3/api-docs",
            "/v3/api-docs/app",
            "/v3/api-docs/common",
            "/v3/api-docs/consulting",
            "/v3/api-docs/admin",
            "/v3/api-docs/merchant"
    );
    private static final Map<String, String> GROUP_TAGS = Map.of(
            "/v3/api-docs/app", "App",
            "/v3/api-docs/common", "Common",
            "/v3/api-docs/consulting", "Consulting",
            "/v3/api-docs/admin", "Admin",
            "/v3/api-docs/merchant", "Merchant"
    );
    private static final List<String> ADMIN_PLACE_CATEGORIES = List.of(
            PlaceCategoryPolicy.RESTAURANT,
            PlaceCategoryPolicy.MUSIC,
            PlaceCategoryPolicy.POP_UP,
            PlaceCategoryPolicy.FASHION,
            PlaceCategoryPolicy.BEAUTY,
            PlaceCategoryPolicy.EXHIBITION,
            PlaceCategoryPolicy.CAFE,
            PlaceCategoryPolicy.CULTURAL_HERITAGE,
            PlaceCategoryPolicy.OTHER
    );
    private static final List<String> ADMIN_PLACE_CATEGORY_NAMES = List.of(
            "음식점",
            "음악",
            "팝업",
            "패션",
            "뷰티",
            "전시",
            "카페",
            "문화재",
            "기타",
            "미분류"
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
    void authorizationRulesAreReflectedInEveryGroupedOpenApiDocument() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode availability = appDocument.at("/paths/~1places~1{placeId}~1availabilities/get");

        assertThat(availability.at("/security/0/bearerAuth").isArray()).isTrue();
        assertErrorResponse(availability, "401");
        assertErrorResponse(availability, "403");

        JsonNode commonDocument = readApiDocs("/v3/api-docs/common");
        JsonNode reviews = commonDocument.at("/paths/~1places~1{placeId}~1reviews/get");

        assertThat(reviews.at("/security/0/bearerAuth").isArray()).isTrue();
        assertErrorResponse(reviews, "401");

        JsonNode publicAnalysis = appDocument.at("/paths/~1analysis~1reports~1location/post");
        assertThat(publicAnalysis.path("security").isMissingNode()).isTrue();
        assertThat(publicAnalysis.path("responses").has("401")).isFalse();

        JsonNode fcmRegistration = appDocument.at("/paths/~1firebase~1fcm-tokens/post");
        assertThat(fcmRegistration.path("responses").has("400")).isTrue();
        assertThat(fcmRegistration.at("/responses/400/content/application~1json/schema/oneOf").toString())
                .contains("ErrorResponse", "ValidationErrorResponse");
    }

    @Test
    void allProtectedOperationsDeclareJwtAndCommonAuthorizationFailures() throws Exception {
        for (String apiDocPath : API_DOC_PATHS) {
            JsonNode paths = readApiDocs(apiDocPath).path("paths");
            for (Iterator<Entry<String, JsonNode>> pathIterator = paths.fields(); pathIterator.hasNext(); ) {
                Entry<String, JsonNode> pathEntry = pathIterator.next();
                if (isPublicPath(pathEntry.getKey())) {
                    continue;
                }

                for (Iterator<Entry<String, JsonNode>> operationIterator = pathEntry.getValue().fields(); operationIterator.hasNext(); ) {
                    Entry<String, JsonNode> operationEntry = operationIterator.next();
                    if (!isHttpMethod(operationEntry.getKey())) {
                        continue;
                    }

                    JsonNode operation = operationEntry.getValue();
                    String location = apiDocPath + " " + operationEntry.getKey().toUpperCase() + " " + pathEntry.getKey();
                    assertThat(operation.at("/security/0/bearerAuth").isArray()).as(location).isTrue();
                    assertErrorResponse(operation, "401");
                    assertErrorResponse(operation, "403");
                }
            }
        }
    }

    @Test
    void groupedDocumentsMatchSwaggerTagsWithoutOmittingTaggedOperations() throws Exception {
        JsonNode allDocument = readApiDocs("/v3/api-docs");

        for (Entry<String, String> group : GROUP_TAGS.entrySet()) {
            JsonNode groupDocument = readApiDocs(group.getKey());
            assertGroupedDocumentContainsOnlyTag(groupDocument, group.getValue(), group.getKey());
            assertAllTaggedOperationsAreIncluded(allDocument, groupDocument, group.getValue(), group.getKey());
        }
    }

    @Test
    void adminDashboardRecentActivitiesAndPendingItemsAreDocumentedInWebGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/admin/dashboard/recent-activities")).isFalse();
        assertThat(appDocument.path("paths").has("/admin/dashboard/pending-items")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/dashboard/recent-activities")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/dashboard/pending-items")).isTrue();

        JsonNode recentActivities = webDocument.at("/paths/~1admin~1dashboard~1recent-activities/get");
        JsonNode pendingItems = webDocument.at("/paths/~1admin~1dashboard~1pending-items/get");

        assertThat(recentActivities.path("operationId").asText()).isEqualTo("getRecentActivities");
        assertThat(recentActivities.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminDashboardRecentActivitiesResponse");
        assertThat(recentActivities.path("responses").has("401")).isTrue();
        assertThat(recentActivities.path("responses").has("403")).isTrue();
        assertThat(recentActivities.at("/responses/401/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(recentActivities.at("/responses/403/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertLimitParameter(recentActivities.path("parameters").path(0));

        assertThat(pendingItems.path("operationId").asText()).isEqualTo("getPendingItems");
        assertThat(pendingItems.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminDashboardPendingItemsResponse");
        assertThat(pendingItems.path("responses").has("401")).isTrue();
        assertThat(pendingItems.path("responses").has("403")).isTrue();
        assertThat(pendingItems.at("/responses/403/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(pendingItems.at("/responses/401/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertLimitParameter(pendingItems.path("parameters").path(0));

        JsonNode schemas = webDocument.path("components").path("schemas");
        assertThat(schemas.has("AdminDashboardRecentPlaceItem")).isTrue();
        assertThat(schemas.at("/AdminDashboardRecentPlaceItem/properties/createdAt").path("format").asText())
                .isEqualTo("date-time");
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentPostItem")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentReportItem")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentUserSanctionItem")).isTrue();
        assertThat(schemas.has("AdminDashboardPendingItem")).isTrue();
        assertThat(schemas.at("/AdminDashboardPendingItem/properties/type/enum").toString())
                .contains("POST_REPORT", "MERCHANT_PLACE_APPLICATION");
        assertThat(schemas.at("/AdminDashboardPendingItem/properties/reportId").path("type").asText())
                .isEqualTo("integer");
        assertThat(schemas.at("/AdminDashboardPendingItem/properties/postId").path("nullable").asBoolean())
                .isTrue();
        assertThat(schemas.at("/AdminDashboardPendingItem/properties/navigationPath").path("nullable").asBoolean())
                .isTrue();
        assertThat(schemas.at("/AdminDashboardPendingItemsResponse/properties/totalCount").path("type").asText())
                .isEqualTo("integer");
    }

    @Test
    void discoveryAndRecommendationContractsExposeFiltersBoundsAndFailureResponses() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode discovery = appDocument.at("/paths/~1places/get");
        JsonNode recommendation = appDocument.at("/paths/~1places~1recommendations/get");

        assertThat(discovery.path("responses").path("200").path("content").path("*/*")
                .path("schema").path("$ref").asText())
                .as("Discovery 정상 응답은 장소 목록 계약을 사용해야 한다")
                .isEqualTo("#/components/schemas/PlaceListResponse");
        assertThat(discovery.path("parameters").toString())
                .as("Discovery 계약은 페이지·키워드·카테고리·위치 필터를 포함해야 한다")
                .contains("page", "limit", "keyword", "category", "touristCategory", "latitude", "longitude", "radiusKm");
        assertThat(discovery.at("/parameters/1/schema/minimum").asInt())
                .as("Discovery limit 하한은 1이어야 한다")
                .isEqualTo(1);
        assertThat(discovery.at("/parameters/1/schema/maximum").asInt())
                .as("Discovery limit 상한은 100이어야 한다")
                .isEqualTo(100);

        assertThat(recommendation.path("responses").path("200").path("content").path("*/*")
                .path("schema").path("$ref").asText())
                .as("Recommendation 정상 응답은 추천 목록 계약을 사용해야 한다")
                .isEqualTo("#/components/schemas/PlaceRecommendationResponse");
        assertThat(recommendation.path("responses").path("400").path("content").path("*/*")
                .path("example").path("message").asText())
                .as("잘못된 좌표 실패 원인을 응답 예시로 식별할 수 있어야 한다")
                .contains("-90.0");
        assertThat(recommendation.path("responses").path("401").path("content").path("*/*")
                .path("example").path("code").asText())
                .as("추천 인증 실패는 오류 코드를 포함해야 한다")
                .isEqualTo("INVALID_TOKEN");
        assertThat(recommendation.path("parameters").toString())
                .as("Recommendation 계약은 좌표·limit·반경·버전 조건을 포함해야 한다")
                .contains("latitude", "longitude", "limit", "radiusKm", "recommendationVersion");
        assertThat(recommendation.at("/parameters/2/schema/minimum").asInt())
                .as("Recommendation limit 하한은 1이어야 한다")
                .isEqualTo(1);
        assertThat(recommendation.at("/parameters/2/schema/maximum").asInt())
                .as("Recommendation limit 상한은 20이어야 한다")
                .isEqualTo(20);
        JsonNode recommendationItem = appDocument.at("/components/schemas/PlaceRecommendationItem/properties");
        assertThat(recommendationItem.has("currentlyOperating"))
                .as("추천 항목은 현재 영업 여부를 제공해야 한다")
                .isTrue();
        assertThat(recommendationItem.has("currentlyOperatingCheckedAt"))
                .as("추천 항목은 현재 영업 여부 판정 시각을 제공해야 한다")
                .isTrue();
        assertThat(recommendationItem.has("hasActiveBenefit"))
                .as("추천 항목은 현재 이용 가능한 혜택 존재 여부를 제공해야 한다")
                .isTrue();
        assertThat(recommendationItem.has("reservable"))
                .as("추천 항목은 현재 예약 가능 여부를 제공해야 한다")
                .isTrue();
        assertThat(recommendationItem.has("reasonCode"))
                .as("추천 항목은 기계 판독 가능한 추천 사유 코드를 제공해야 한다")
                .isTrue();
        assertThat(appDocument.at("/components/schemas/PlaceRecommendationResponse/properties/limitReasons")
                .has("items"))
                .as("추천 응답은 제한 사유 코드 목록을 제공해야 한다")
                .isTrue();
        JsonNode explanationItem = appDocument.at(
                "/components/schemas/PlaceRecommendationExplanationItem/properties"
        );
        assertThat(explanationItem.has("benefitScore")).isTrue();
        assertThat(explanationItem.has("availabilityScore")).isTrue();
    }

    @Test
    void adminPlaceContractsExposeCanonicalCategoryAndLevel() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode operation = webDocument.at("/paths/~1admin~1places/get");
        JsonNode categoryParameter = parameter(operation, "category");

        assertThat(categoryParameter.isMissingNode()).isFalse();
        assertThat(categoryParameter.path("required").asBoolean()).isFalse();
        assertThat(categoryParameter.path("example").asText()).isEqualTo(PlaceCategoryPolicy.CAFE);
        assertThat(categoryParameter.path("description").asText())
                .contains("AdminMapPlaceItem.category", "touristCategories");
        assertThat(categoryParameter.path("schema").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(ADMIN_PLACE_CATEGORIES);

        JsonNode emptyResult = operation.at("/responses/200/content/*~1*/examples/emptyResult/value");
        assertThat(emptyResult.path("places").isArray()).isTrue();
        assertThat(emptyResult.path("places").isEmpty()).isTrue();
        assertThat(emptyResult.path("totalCount").asLong()).isZero();
        assertThat(emptyResult.path("totalPages").asLong()).isEqualTo(1L);
        assertThat(emptyResult.path("hasNext").asBoolean()).isFalse();

        for (String schemaName : List.of("AdminMapPlaceItem", "AdminMapPlaceDetailResponse")) {
            JsonNode schema = webDocument.at("/components/schemas/" + schemaName);
            JsonNode properties = schema.path("properties");

            assertThat(properties.path("category").path("enum"))
                    .extracting(JsonNode::asText)
                    .containsExactlyInAnyOrderElementsOf(ADMIN_PLACE_CATEGORIES);
            assertThat(properties.path("categoryName").path("enum"))
                    .extracting(JsonNode::asText)
                    .containsExactlyInAnyOrderElementsOf(ADMIN_PLACE_CATEGORY_NAMES);
            assertThat(properties.path("touristCategories").path("description").asText())
                    .contains("별도 기준");
            assertThat(properties.path("level").path("type").asText()).isEqualTo("integer");
            assertThat(properties.path("level").path("minimum").asInt()).isZero();
            assertThat(requiredFields(schema)).contains("level");
            assertNullableProperty(webDocument, schemaName, "category");
        }
        JsonNode detailSchema = webDocument.at("/components/schemas/AdminMapPlaceDetailResponse");
        assertNullableProperty(webDocument, "AdminMapPlaceDetailResponse", "imageUrl");
        assertThat(requiredFields(detailSchema)).contains("imageUrl");
        assertThat(webDocument.at("/components/schemas/AdminMapPlaceResponse/properties/totalPages/minimum")
                .asLong()).isEqualTo(1L);
    }

    @Test
    void locationCheckInApisAreExposedInAppGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertThat(appDocument.path("paths").has("/location-check-ins")).isTrue();
        assertThat(appDocument.at("/paths/~1location-check-ins/post/requestBody/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/LocationCheckInRequest");
        assertThat(appDocument.at("/paths/~1location-check-ins/post/responses/201/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/LocationCheckInResponse");
        for (String status : List.of("400", "401", "403", "404", "409", "422")) {
            assertThat(appDocument.at("/paths/~1location-check-ins/post/responses/" + status
                    + "/content/*~1*/schema/$ref").asText()).isEqualTo("#/components/schemas/ErrorResponse");
        }
    }

    @Test
    void visitEvidenceApisExposeSecuritySuccessAndFailureContracts() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode evidence = appDocument.at("/paths/~1location-check-ins~1{checkInId}~1evidence");
        assertThat(evidence.path("post").at("/requestBody/content/multipart~1form-data/schema/properties/file/format")
                .asText()).isEqualTo("binary");
        assertThat(evidence.path("post").path("security").toString()).contains("bearerAuth");
        assertThat(evidence.at("/post/responses/201/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/VisitEvidenceResponse");
        for (String status : List.of("400", "404", "409", "413", "503")) {
            assertThat(evidence.at("/post/responses/" + status + "/content/*~1*/schema/$ref").asText())
                    .as("증빙 업로드 %s 응답은 공통 오류 계약을 사용해야 한다", status)
                    .isEqualTo("#/components/schemas/ErrorResponse");
        }
        assertThat(evidence.path("get").path("responses").has("404")).isTrue();

        JsonNode evidenceFile = appDocument.at(
                "/paths/~1location-check-ins~1{checkInId}~1evidence~1file/get");
        assertThat(evidenceFile.at("/responses/200/content/*~1*/schema/format").asText()).isEqualTo("byte");
        assertThat(evidenceFile.path("security").toString()).contains("bearerAuth");
    }

    @Test
    void scoutFieldReportApisAreSeparatedIntoAppAndWebGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/scout-field-reports")).isTrue();
        assertThat(appDocument.path("paths").has("/scout-field-reports/{reportId}")).isTrue();
        assertThat(appDocument.path("paths").has("/admin/scout-field-reports")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/scout-field-reports")).isTrue();
        assertThat(webDocument.path("paths").has("/scout-field-reports")).isFalse();

        assertThat(appDocument.at(
                "/paths/~1scout-field-reports/post/requestBody/content/application~1json/schema/$ref"
        ).asText()).isEqualTo("#/components/schemas/ScoutFieldReportCreateRequest");
        assertThat(appDocument.at(
                "/paths/~1scout-field-reports/post/responses/201/content/*~1*/schema/$ref"
        ).asText()).isEqualTo("#/components/schemas/MyScoutFieldReportResponse");
        assertThat(webDocument.at(
                "/paths/~1admin~1scout-field-reports~1{reportId}~1review/post/requestBody/content/application~1json/schema/$ref"
        ).asText()).isEqualTo("#/components/schemas/ScoutFieldReportReviewRequest");
        assertThat(resolveSchema(
                appDocument,
                appDocument.at("/components/schemas/ScoutFieldReportCreateRequest/properties/reportType")
        ).path("enum"))
                .extracting(JsonNode::asText)
                .contains("PLACE_INFORMATION", "SAFETY", "OTHER");
    }

    @Test
    void removedDeprecatedEndpointsAreAbsentFromApiDocs() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertThat(appDocument.path("paths").has("/firebase/fcm-token")).isFalse();
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
        JsonNode travelSchedulePostResponses = appDocument.at("/paths/~1users~1me~1travel-schedules/post/responses");
        assertThat(travelSchedulePostResponses.has("400")).isTrue();
        assertThat(travelSchedulePostResponses.has("409")).isTrue();
        JsonNode travelSchedulePatchResponses = appDocument.at("/paths/~1users~1me~1travel-schedules~1{scheduleId}/patch/responses");
        assertThat(travelSchedulePatchResponses.has("400")).isTrue();
        assertThat(travelSchedulePatchResponses.has("409")).isTrue();
        assertThat(appDocument.at("/paths/~1users~1me~1current-activity-intent/put/requestBody/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/CurrentActivityIntentUpdateRequest");
        assertThat(appDocument.at("/paths/~1users~1me~1current-activity-intent/get/responses/200/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/CurrentActivityIntentResponse");
    }

    @Test
    void merchantOwnerAndPlaceRegistrationApisAreSeparatedIntoGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        assertThat(appDocument.path("paths").has("/users/me/place-registration-applications")).isFalse();
        assertThat(appDocument.path("paths").has("/places/coordinates")).isFalse();
        assertThat(appDocument.path("paths").has("/places/upload")).isFalse();
        assertThat(appDocument.path("paths").has("/map/posts")).isFalse();

        for (String path : List.of(
                "/users/me/merchant-owner-profile",
                "/users/me/merchant-place-applications",
                "/merchant-owner/me",
                "/merchant-owner/places/{placeId}/information",
                "/merchant-owner/places/{placeId}/reviews",
                "/users/me/merchant-place-applications/{applicationId}/attachments",
                "/users/me/merchant-place-applications/{applicationId}/attachments/{attachmentId}",
                "/users/me/merchant-place-applications/{applicationId}/attachments/reorder"
        )) {
            assertThat(merchantDocument.path("paths").has(path)).as("Merchant 경로: %s", path).isTrue();
            assertThat(appDocument.path("paths").has(path)).as("App에 노출되지 않아야 함: %s", path).isFalse();
        }

        for (String path : List.of(
                "/admin/merchant-owners",
                "/admin/merchant-owners/{userId}/approve",
                "/admin/merchant-owners/{userId}/reject",
                "/admin/merchant-owners/{userId}/onboarding",
                "/admin/merchant-place-applications"
        )) {
            assertThat(adminDocument.path("paths").has(path)).as("Admin 경로: %s", path).isTrue();
            assertThat(merchantDocument.path("paths").has(path)).as("Merchant에 노출되지 않아야 함: %s", path).isFalse();
        }
    }

    @Test
    void adminMerchantPlaceApplicationStatusFilterSupportsRepeatedEnumValues() throws Exception {
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode operation = adminDocument.at("/paths/~1admin~1merchant-place-applications/get");
        JsonNode statusParameter = parameter(operation, "status");
        JsonNode applicationTypeParameter = parameter(operation, "applicationType");

        assertThat(statusParameter.path("in").asText()).isEqualTo("query");
        assertThat(statusParameter.path("style").asText()).isEqualTo("form");
        assertThat(statusParameter.path("explode").asBoolean()).isTrue();
        assertThat(statusParameter.path("schema").path("type").asText()).isEqualTo("array");
        assertThat(resolveSchema(adminDocument, statusParameter.path("schema").path("items")).path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        "DRAFT", "PENDING", "APPROVED", "REJECTED", "COMPLETED", "CANCELED"
                );
        assertThat(applicationTypeParameter.path("schema").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("NEW_PLACE", "EXISTING_PLACE_CLAIM");
    }

    @Test
    void removedLegacyApiDocumentationDoesNotAppear() throws Exception {
        JsonNode defaultDocument = readApiDocs("/v3/api-docs");
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        for (String hiddenPath : List.of(
                "/place/recommendations",
                "/place/recommendations/click",
                "/place/recommendations/{requestId}/explanation"
        )) {
            assertThat(defaultDocument.path("paths").has(hiddenPath))
                    .as("기존 추천 호환 경로는 OpenAPI에서 숨겨져야 함: %s", hiddenPath)
                    .isFalse();
        }

        for (String removedPath : List.of(
                "/map/report-appeals", "/map/posts", "/map/posts/{id}", "/map/posts/{id}/report",
                "/map/reports", "/map/place-rankings", "/map/bookmarks", "/map/likes", "/map/like",
                "/map/like/{postId}", "/map/like/return/{postId}/{notificationsId}",
                "/users/me/place-registration-applications"
        )) {
            assertThat(appDocument.path("paths").has(removedPath)).isFalse();
        }
        for (String removedPath : List.of(
                "/users/me/merchant-verification",
                "/merchant-owner/place-claims",
                "/merchant-owner/place-claims/{claimId}",
                "/merchant-owner/place-claims/{claimId}/cancel",
                "/merchant-owner/place-claims/{claimId}/attachments"
        )) {
            assertThat(merchantDocument.path("paths").has(removedPath)).isFalse();
        }
        for (String removedPath : List.of(
                "/admin/merchant-verifications",
                "/admin/merchant-place-claims",
                "/admin/place-registration-applications"
        )) {
            assertThat(adminDocument.path("paths").has(removedPath)).isFalse();
        }
        assertThat(defaultDocument.path("paths").has("/auth/google")).isFalse();
        assertThat(adminDocument.path("paths").has("/admin/ad")).isFalse();
        assertThat(parameter(adminDocument.at("/paths/~1admin~1notifications/get"), "userId")
                .isMissingNode()).isTrue();

        JsonNode bannedUsersOperation = adminDocument.at("/paths/~1admin~1users~1banned/get");
        for (String legacyParameterName : List.of("bannedFrom", "bannedTo")) {
            JsonNode legacyParameter = parameter(bannedUsersOperation, legacyParameterName);
            assertThat(legacyParameter.isMissingNode()).isTrue();
        }
    }

    @Test
    void touristOfferAndCouponApisAreSeparatedIntoAppAndMerchantGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        for (String path : List.of("/offers", "/offers/{offerId}", "/offers/{offerId}/coupons", "/coupons")) {
            assertThat(appDocument.path("paths").has(path)).as("App 경로: %s", path).isTrue();
            assertThat(merchantDocument.path("paths").has(path)).as("Merchant에 노출되지 않아야 함: %s", path).isFalse();
        }
        for (String path : List.of(
                "/merchant-owner/offers",
                "/merchant-owner/offers/{offerId}",
                "/merchant-owner/offers/{offerId}/publish",
                "/merchant-owner/offers/{offerId}/close",
                "/merchant-owner/offers/coupons/redeem"
        )) {
            assertThat(merchantDocument.path("paths").has(path)).as("Merchant 경로: %s", path).isTrue();
            assertThat(appDocument.path("paths").has(path)).as("App에 노출되지 않아야 함: %s", path).isFalse();
        }
    }

    @Test
    void touristInformationSchemasDeclareNullableStringFields() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");

        for (String schemaName : List.of(
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
    void adminPlacePostVisibilityContractIsDocumented() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode postSchema = webDocument.at("/components/schemas/AdminMapPlaceImageItem");

        assertThat(resolveSchema(webDocument, postSchema.at("/properties/visibilityStatus")).path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
        assertNullableProperty(webDocument, "AdminMapPlaceImageItem", "hiddenReason");
    }

    @Test
    void adminPlaceGrowthPhotoCountsAreDocumented() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode detailSchema = webDocument.at("/components/schemas/AdminMapPlaceDetailResponse");
        JsonNode growthSchema = webDocument.at("/components/schemas/AdminMapPlaceGrowthResponse");

        assertThat(detailSchema.at("/properties/placeGrowth/$ref").asText())
                .isEqualTo("#/components/schemas/AdminMapPlaceGrowthResponse");
        assertThat(growthSchema.at("/properties/photoCount/description").asText())
                .contains("성장에 반영된", "노출");
        assertThat(growthSchema.at("/properties/hiddenPhotoCount/description").asText())
                .contains("성장에 반영되지 않는", "숨김");
    }

    @Test
    void placeDiscoveryFilterSortContractsAreDocumented() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        JsonNode listPlacesOperation = appDocument.at("/paths/~1places/get");
        assertThat(listPlacesOperation.isMissingNode()).isFalse();
        assertThat(parameter(listPlacesOperation, "touristCategory").path("example").asText()).isEqualTo("K_POP");
        assertThat(parameter(listPlacesOperation, "sort").path("description").asText())
                .contains("LATEST", "NEAREST", "POPULAR");
        assertThat(listPlacesOperation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PlaceListResponse");

        JsonNode placeListItemProperties = appDocument.at("/components/schemas/PlaceListItem/properties");
        assertThat(placeListItemProperties.has("touristCategories")).isTrue();
        assertThat(placeListItemProperties.has("distanceMeters")).isTrue();

        JsonNode clickRequestSchema = appDocument.at("/components/schemas/PlaceRecommendationClickRequest");
        assertThat(requiredFields(clickRequestSchema))
                .contains("placeId", "recommendationVersion", "requestId");

        assertThat(appDocument.path("paths").has("/admin/places/{id}/discovery-status")).isFalse();
        JsonNode discoveryStatusOperation = webDocument.at("/paths/~1admin~1places~1{id}~1discovery-status/patch");
        assertThat(discoveryStatusOperation.isMissingNode()).isFalse();
        assertThat(discoveryStatusOperation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminMapPlaceDiscoveryStatusUpdateRequest");
        assertThat(discoveryStatusOperation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminMapPlaceDiscoveryStatusUpdateResponse");

        JsonNode discoveryRequestSchema = webDocument.at("/components/schemas/AdminMapPlaceDiscoveryStatusUpdateRequest");
        assertThat(requiredFields(discoveryRequestSchema)).contains("discoveryStatus", "reason");
        for (String schemaName : List.of(
                "AdminMapPlaceItem",
                "AdminMapPlaceDetailResponse",
                "AdminMapPlaceDiscoveryStatusUpdateRequest",
                "AdminMapPlaceDiscoveryStatusUpdateResponse"
        )) {
            JsonNode discoveryStatusSchema = webDocument.at(
                    "/components/schemas/" + schemaName + "/properties/discoveryStatus"
            );
            assertThat(resolveSchema(webDocument, discoveryStatusSchema).path("enum"))
                    .extracting(JsonNode::asText)
                    .containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
        }
    }

    @Test
    void touristPlaceCardContractIsDocumentedInAppGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode operation = appDocument.at("/paths/~1places~1{placeId}~1card/get");

        assertThat(operation.isMissingNode()).as("관광객 장소 카드 조회 경로가 app 문서에 있어야 한다").isFalse();
        assertThat(operation.path("parameters").size()).isEqualTo(1);
        assertThat(operation.at("/parameters/0/name").asText()).isEqualTo("placeId");
        assertThat(operation.at("/parameters/0/in").asText()).isEqualTo("path");
        assertThat(operation.at("/parameters/0/required").asBoolean()).isTrue();
        assertThat(operation.at("/parameters/0/schema/type").asText()).isEqualTo("integer");
        assertThat(operation.at("/parameters/0/schema/format").asText()).isEqualTo("int64");
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/TouristPlaceCardResponse");
        assertThat(operation.at("/responses/404/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");

        JsonNode cardSchema = appDocument.at("/components/schemas/TouristPlaceCardResponse");
        assertThat(cardSchema.isMissingNode()).isFalse();
        assertThat(cardSchema.path("properties").has("currentlyOperating")).isTrue();
        assertThat(cardSchema.path("properties").has("touristCategories")).isTrue();
        assertThat(cardSchema.path("properties").has("primaryInformationSource")).isTrue();
        assertThat(cardSchema.path("properties").has("informationVerificationStatus")).isTrue();
        assertThat(cardSchema.path("properties").has("verifiedEvidenceCount")).isTrue();
        assertThat(cardSchema.path("properties").has("lastVerifiedAt")).isTrue();
        assertThat(cardSchema.path("properties").has("lastVerifiedSourceType")).isTrue();
        assertThat(resolveSchema(appDocument, cardSchema.at("/properties/operatingStatus"))
                .path("enum")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("OPERATING", "TEMPORARILY_CLOSED", "PERMANENTLY_CLOSED");
    }

    @Test
    void placeVisitDecisionContractIsDocumentedInAppGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode operation = appDocument.at("/paths/~1places~1{placeId}~1visit-decision/get");

        assertThat(operation.isMissingNode()).as("방문 결정 조회 경로가 app 문서에 있어야 한다").isFalse();
        assertThat(operation.at("/parameters/0/name").asText()).isEqualTo("placeId");
        assertThat(operation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/PlaceVisitDecisionResponse");
        assertThat(operation.at("/responses/401/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(operation.at("/responses/404/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/ErrorResponse");

        JsonNode responseSchema = appDocument.at("/components/schemas/PlaceVisitDecisionResponse/properties");
        assertThat(responseSchema.has("place")).isTrue();
        assertThat(responseSchema.has("merchantInformation")).isTrue();
        assertThat(responseSchema.at("/ongoingEvents/type").asText()).isEqualTo("array");
        assertThat(responseSchema.at("/reservableAvailabilities/type").asText()).isEqualTo("array");
        assertThat(responseSchema.has("availableOffers")).isTrue();
        assertThat(responseSchema.at("/checkedAt/format").asText()).isEqualTo("date-time");
    }

    @Test
    void placeVisitDecisionContractIsNotExposedInWebGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/places/{placeId}/visit-decision")).isTrue();
        assertThat(webDocument.path("paths").has("/places/{placeId}/visit-decision")).isFalse();
    }

    @Test
    void placeVisitDecisionMerchantSchemaDoesNotExposeEditorIdentity() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantSchema = appDocument.at(
                "/components/schemas/PlaceVisitDecisionMerchantInformationResponse/properties"
        );

        assertThat(merchantSchema.has("description")).isTrue();
        assertThat(merchantSchema.has("reservationUrl")).isTrue();
        assertThat(merchantSchema.has("updatedAt")).isTrue();
        assertThat(merchantSchema.has("updatedByUserId")).isFalse();
    }

    @Test
    void placeExplorationContractsExposeSecurityErrorsAndStableSchemas() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertAuthenticatedOperation(appDocument, "/places/map", "get", "200", "400", "401");
        assertAuthenticatedOperation(appDocument, "/places/{placeId}/card", "get", "200", "401", "404");
        assertAuthenticatedOperation(
                appDocument,
                "/places/{placeId}/visit-decision",
                "get",
                "200",
                "401",
                "404"
        );
        assertAuthenticatedOperation(
                appDocument,
                "/places/{placeId}/operating-notices",
                "get",
                "200",
                "401",
                "404"
        );
        assertAuthenticatedOperation(
                appDocument,
                "/places/{id}/media/verification",
                "get",
                "200",
                "401",
                "403",
                "404"
        );
        assertAuthenticatedOperation(
                appDocument,
                "/places/recommendations/{requestId}/explanation",
                "get",
                "200",
                "401",
                "404"
        );
        assertAuthenticatedOperation(
                appDocument,
                "/places/{placeId}/map-link-conversions",
                "post",
                "204",
                "400",
                "401"
        );

        JsonNode conversionOperation = appDocument.at(
                "/paths/~1places~1{placeId}~1map-link-conversions/post"
        );
        assertThat(conversionOperation.at("/responses/204/content").isMissingNode()).isTrue();
        assertThat(requiredFields(appDocument.at("/components/schemas/MapLinkConversionRequest")))
                .containsExactlyInAnyOrder("linkType", "provider", "requestId");

        JsonNode mapOperation = appDocument.at("/paths/~1places~1map/get");
        assertParameterRange(mapOperation, "west", -180.0, 180.0);
        assertParameterRange(mapOperation, "south", -90.0, 90.0);
        assertParameterRange(mapOperation, "east", -180.0, 180.0);
        assertParameterRange(mapOperation, "north", -90.0, 90.0);
        assertParameterRange(mapOperation, "zoom", 0.0, 20.0);

        JsonNode viewportSchema = appDocument.at("/components/schemas/MapViewportResponse");
        assertThat(requiredFields(viewportSchema))
                .containsExactlyInAnyOrder("mode", "zoom", "clusters", "markers", "truncated");
        assertThat(viewportSchema.at("/properties/mode/enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("MARKERS", "CLUSTERS");

        assertThat(requiredFields(appDocument.at("/components/schemas/TouristPlaceCardResponse")))
                .contains("id", "name", "currentlyOperating", "touristCategories", "latitude", "longitude");
        assertThat(appDocument.at("/components/schemas/TouristPlaceCardResponse/properties/imageUrl/nullable")
                .asBoolean()).isTrue();
        assertThat(requiredFields(appDocument.at("/components/schemas/PlaceVisitDecisionResponse")))
                .containsExactlyInAnyOrder(
                        "place",
                        "merchantInformation",
                        "ongoingEvents",
                        "reservableAvailabilities",
                        "availableOffers",
                        "checkedAt"
                );
        JsonNode merchantInformationSchema = appDocument.at(
                "/components/schemas/PlaceVisitDecisionResponse/properties/merchantInformation"
        );
        assertThat(merchantInformationSchema.path("nullable").asBoolean())
                .as("merchantInformation must be nullable: %s", merchantInformationSchema)
                .isTrue();
        JsonNode merchantOwnerSchema = appDocument.at(
                "/components/schemas/PlaceDetailResponse/properties/merchantOwner"
        );
        assertThat(merchantOwnerSchema.path("nullable").asBoolean())
                .as("merchantOwner must be nullable: %s", merchantOwnerSchema)
                .isTrue();
        assertThat(requiredFields(appDocument.at("/components/schemas/PlaceOperatingNoticeListResponse")))
                .containsExactlyInAnyOrder("placeId", "currentlyOperating", "checkedAt", "notices");
        assertThat(requiredFields(appDocument.at("/components/schemas/PlaceMediaResponse")))
                .containsExactlyInAnyOrder("placeId", "media");

        JsonNode errorSchema = appDocument.at("/components/schemas/ErrorResponse");
        assertThat(requiredFields(errorSchema)).containsExactly("message");
        assertThat(errorSchema.at("/properties/code/nullable").asBoolean()).isTrue();
        assertThat(appDocument.at(
                "/components/schemas/PlaceRecommendationExplanationItem/properties/source/example"
        ).asText()).isEqualTo("PERSONAL");
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
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/events")).isTrue();
        assertThat(appDocument.path("paths").has("/admin/place-events")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/place-events")).isTrue();
    }

    @Test
    void placeInformationReportDisputeContractsAreSeparatedIntoAppAndWebGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        for (String path : List.of(
                "/places/{placeId}/information-reports",
                "/places/information-reports",
                "/places/information-reports/{reportId}",
                "/places/information-reports/{reportId}/disputes"
        )) {
            assertThat(appDocument.path("paths").has(path))
                    .as("%s must be exposed in app OpenAPI group", path)
                    .isTrue();
            assertThat(webDocument.path("paths").has(path))
                    .as("%s must not leak into web OpenAPI group", path)
                    .isFalse();
        }

        for (String path : List.of(
                "/admin/place-information-reports",
                "/admin/place-information-reports/{reportId}",
                "/admin/place-information-reports/{reportId}/review",
                "/admin/place-information-reports/{reportId}/disputes/{disputeId}/review"
        )) {
            assertThat(webDocument.path("paths").has(path))
                    .as("%s must be exposed in web OpenAPI group", path)
                    .isTrue();
            assertThat(appDocument.path("paths").has(path))
                    .as("%s must not leak into app OpenAPI group", path)
                    .isFalse();
        }

        JsonNode createRequestSchema = appDocument.at("/components/schemas/PlaceInformationReportCreateRequest");
        assertThat(requiredFields(createRequestSchema))
                .as("신고 생성 요청은 대상/사유를 계약상 필수로 노출해야 한다")
                .contains("targetType", "reasonType");
        assertThat(resolveSchema(appDocument, createRequestSchema.at("/properties/targetType")).path("enum"))
                .extracting(JsonNode::asText)
                .contains("OPERATING_STATUS", "TOURIST_INFORMATION", "SOURCE_EVIDENCE");
        assertThat(resolveSchema(appDocument, createRequestSchema.at("/properties/reasonType")).path("enum"))
                .extracting(JsonNode::asText)
                .contains("INCORRECT", "OUTDATED", "MISSING");

        assertThat(appDocument.at("/components/schemas/PlaceInformationReportResponse/properties/status/enum"))
                .extracting(JsonNode::asText)
                .as("신고 응답은 반박/해결 상태까지 계약에 포함해야 한다")
                .contains("SUBMITTED", "UNDER_REVIEW", "ACCEPTED", "DISPUTED", "RESOLVED");
        assertThat(appDocument.at("/components/schemas/PlaceInformationDisputeResponse/properties/status/enum"))
                .extracting(JsonNode::asText)
                .as("반박 응답은 제출/승인/거절 상태를 계약에 포함해야 한다")
                .contains("SUBMITTED", "ACCEPTED", "REJECTED");
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

    private void assertAuthenticatedOperation(
            JsonNode document,
            String path,
            String method,
            String successStatus,
            String... errorStatuses
    ) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode())
                .as("%s %s operation must exist", method.toUpperCase(), path)
                .isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray())
                .as("%s %s must require bearerAuth", method.toUpperCase(), path)
                .isTrue();
        assertThat(operation.path("responses").has(successStatus))
                .as("%s %s must expose %s", method.toUpperCase(), path, successStatus)
                .isTrue();

        for (String errorStatus : errorStatuses) {
            assertThat(operation.at("/responses/" + errorStatus + "/content/*~1*/schema/$ref").asText())
                    .as("%s %s %s must use ErrorResponse", method.toUpperCase(), path, errorStatus)
                    .isEqualTo("#/components/schemas/ErrorResponse");
        }
    }

    private void assertParameterRange(
            JsonNode operation,
            String parameterName,
            double expectedMinimum,
            double expectedMaximum
    ) {
        JsonNode schema = parameter(operation, parameterName).path("schema");
        assertThat(schema.path("minimum").asDouble())
                .as("%s minimum", parameterName)
                .isEqualTo(expectedMinimum);
        assertThat(schema.path("maximum").asDouble())
                .as("%s maximum", parameterName)
                .isEqualTo(expectedMaximum);
    }

    private JsonNode readApiDocs(String apiDocPath) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(apiDocPath))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
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

    private JsonNode parameter(JsonNode operation, String parameterName) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameterName.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return objectMapper.missingNode();
    }

    private List<String> requiredFields(JsonNode schema) {
        List<String> fields = new ArrayList<>();
        schema.path("required").forEach(field -> fields.add(field.asText()));
        return fields;
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

    private void assertGroupedDocumentContainsOnlyTag(JsonNode document, String expectedTag, String apiDocPath) {
        JsonNode paths = document.path("paths");
        for (Iterator<Entry<String, JsonNode>> pathIterator = paths.fields(); pathIterator.hasNext(); ) {
            Entry<String, JsonNode> pathEntry = pathIterator.next();
            for (Iterator<Entry<String, JsonNode>> operationIterator = pathEntry.getValue().fields(); operationIterator.hasNext(); ) {
                Entry<String, JsonNode> operationEntry = operationIterator.next();
                if (!isHttpMethod(operationEntry.getKey())) {
                    continue;
                }

                assertThat(hasTag(operationEntry.getValue(), expectedTag))
                        .as("%s %s %s는 %s 태그만 포함해야 한다",
                                apiDocPath, operationEntry.getKey().toUpperCase(), pathEntry.getKey(), expectedTag)
                        .isTrue();
            }
        }
    }

    private void assertAllTaggedOperationsAreIncluded(
            JsonNode allDocument,
            JsonNode groupDocument,
            String tagName,
            String groupPath
    ) {
        JsonNode allPaths = allDocument.path("paths");
        for (Iterator<Entry<String, JsonNode>> pathIterator = allPaths.fields(); pathIterator.hasNext(); ) {
            Entry<String, JsonNode> pathEntry = pathIterator.next();
            for (Iterator<Entry<String, JsonNode>> operationIterator = pathEntry.getValue().fields(); operationIterator.hasNext(); ) {
                Entry<String, JsonNode> operationEntry = operationIterator.next();
                if (!isHttpMethod(operationEntry.getKey()) || !hasTag(operationEntry.getValue(), tagName)) {
                    continue;
                }

                JsonNode groupedOperation = groupDocument.path("paths")
                        .path(pathEntry.getKey())
                        .path(operationEntry.getKey());
                assertThat(groupedOperation.isObject())
                        .as("%s %s %s는 %s에 포함되어야 한다",
                                operationEntry.getKey().toUpperCase(), pathEntry.getKey(), tagName, groupPath)
                        .isTrue();
            }
        }
    }

    private boolean hasTag(JsonNode operation, String expectedTag) {
        for (JsonNode tag : operation.path("tags")) {
            if (expectedTag.equals(tag.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean isHttpMethod(String method) {
        return switch (method) {
            case "get", "post", "put", "patch", "delete", "head", "options", "trace" -> true;
            default -> false;
        };
    }

    private boolean isPublicPath(String path) {
        return "/".equals(path)
                || path.startsWith("/auth/")
                || "/consultations/intro".equals(path)
                || path.startsWith("/analysis/reports/");
    }

    private void assertLimitParameter(JsonNode parameter) {
        assertThat(parameter.path("name").asText()).isEqualTo("limit");
        assertThat(parameter.path("in").asText()).isEqualTo("query");
        assertThat(parameter.path("required").asBoolean()).isFalse();
        assertThat(parameter.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(10);
        assertThat(parameter.path("schema").path("minimum").asInt()).isEqualTo(1);
        assertThat(parameter.path("schema").path("maximum").asInt()).isEqualTo(50);
    }

    private void assertErrorResponse(JsonNode operation, String status) {
        assertThat(operation.path("responses").has(status)).isTrue();
        JsonNode content = operation.path("responses").path(status).path("content");
        String schemaReference = content.path("application/json").path("schema").path("$ref").asText();
        if (schemaReference.isBlank()) {
            schemaReference = content.path("*/*").path("schema").path("$ref").asText();
        }
        assertThat(schemaReference)
                .isEqualTo("#/components/schemas/ErrorResponse");
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
