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
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest(properties = "pingdom.dev-profile.enabled=true")
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
    void adminDashboardRecentActivitiesAndPendingItemsAreDocumentedInWebGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

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
                .contains("POST_REPORT");
        assertThat(schemas.at("/AdminDashboardPendingItem/properties/reportId").path("type").asText())
                .isEqualTo("integer");
        assertThat(schemas.at("/AdminDashboardPendingItem/properties/postId").path("nullable").asBoolean())
                .isTrue();
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
    void adminPlaceListContractExposesCategoryFilterAndNormalizedEmptyPage() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");
        JsonNode operation = webDocument.at("/paths/~1admin~1places/get");
        JsonNode categoryParameter = parameter(operation, "category");

        assertThat(categoryParameter.isMissingNode()).isFalse();
        assertThat(categoryParameter.path("required").asBoolean()).isFalse();
        assertThat(categoryParameter.path("description").asText())
                .contains("AdminMapPlaceItem.category", "touristCategories");
        assertThat(categoryParameter.path("schema").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder(
                        PlaceCategoryPolicy.CAFE,
                        PlaceCategoryPolicy.RESTAURANT,
                        PlaceCategoryPolicy.TOURISM,
                        PlaceCategoryPolicy.SCENERY,
                        PlaceCategoryPolicy.CULTURE,
                        PlaceCategoryPolicy.SHOPPING,
                        PlaceCategoryPolicy.ACCOMMODATION,
                        PlaceCategoryPolicy.EXPERIENCE
                );

        JsonNode emptyResult = operation.at("/responses/200/content/*~1*/examples/emptyResult/value");
        assertThat(emptyResult.path("places").isArray()).isTrue();
        assertThat(emptyResult.path("places").isEmpty()).isTrue();
        assertThat(emptyResult.path("totalCount").asLong()).isZero();
        assertThat(emptyResult.path("totalPages").asLong()).isEqualTo(1L);
        assertThat(emptyResult.path("hasNext").asBoolean()).isFalse();

        JsonNode itemProperties = webDocument.at("/components/schemas/AdminMapPlaceItem/properties");
        assertThat(itemProperties.path("category").path("enum"))
                .extracting(JsonNode::asText)
                .contains(PlaceCategoryPolicy.CAFE, PlaceCategoryPolicy.EXPERIENCE);
        assertThat(itemProperties.path("categoryName").path("enum"))
                .extracting(JsonNode::asText)
                .contains("미분류");
        assertThat(itemProperties.path("touristCategories").path("description").asText())
                .contains("별도 기준");
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
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

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
    void merchantOwnerApisAreSeparatedIntoAppAndWebGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

        assertThat(appDocument.path("paths").has("/users/me/merchant-owner-profile")).isTrue();
        assertThat(appDocument.path("paths").has("/users/me/merchant-verification")).isTrue();
        assertThat(appDocument.path("paths").has("/merchant-owner/me")).isTrue();
        assertThat(appDocument.path("paths").has("/merchant-owner/place-claims")).isTrue();
        assertThat(appDocument.path("paths").has("/merchant-owner/place-claims/{claimId}")).isTrue();
        assertThat(appDocument.path("paths").has("/merchant-owner/place-claims/{claimId}/cancel")).isTrue();
        assertThat(appDocument.path("paths").has("/merchant-owner/places/{placeId}/information")).isTrue();
        assertThat(webDocument.path("paths").has("/merchant-owner/places/{placeId}/information")).isFalse();
        assertThat(appDocument.path("paths").has("/admin/merchant-owners")).isFalse();
        assertThat(appDocument.path("paths").has("/admin/merchant-owners/{userId}/onboarding")).isFalse();
        assertThat(appDocument.path("paths").has("/admin/merchant-owners/{userId}/places/{placeId}/quality")).isFalse();
        assertThat(appDocument.path("paths").has("/admin/merchant-verifications")).isFalse();
        assertThat(appDocument.path("paths").has("/admin/merchant-place-claims")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/merchant-owners")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-owners/{userId}/approve")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-owners/{userId}/onboarding")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-owners/{userId}/places/{placeId}/quality")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-verifications")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-verifications/{userId}/review")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-place-claims")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/merchant-place-claims/{claimId}/review")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("MerchantOwnerProfileResponse")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("MerchantVerificationResponse")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("MerchantPlaceClaimResponse")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("MerchantPlaceInformationResponse")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("MerchantPlaceInformationUpdateRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("MerchantOnboardingUpdateRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("MerchantOwnerPlaceQualityUpdateRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("MerchantOwnerPlaceResponse")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminMerchantPlaceClaimResponse")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/users/{userId}/roles")).isTrue();
        assertThat(webDocument.path("paths").has("/admin/users/{userId}/roles/{role}")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminRoleAssignmentRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminRoleAssignmentRevokeRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminRoleAssignmentResponse")).isTrue();
        assertThat(appDocument.at("/components/schemas/MerchantPlaceClaimResponse/properties/claimType/enum"))
                .isNotEmpty();
        assertThat(appDocument.at("/components/schemas/MerchantPlaceClaimResponse/properties")
                .has("previousOwnerUserId")).isFalse();
        assertThat(webDocument.at("/components/schemas/AdminMerchantPlaceClaimResponse/properties/previousOwnerUserId/nullable")
                .asBoolean()).isTrue();

        JsonNode onboardingOperation = webDocument.at("/paths/~1admin~1merchant-owners~1{userId}~1onboarding/put");
        assertThat(onboardingOperation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantOnboardingUpdateRequest");
        assertThat(onboardingOperation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantOwnerProfileResponse");

        JsonNode qualityOperation = webDocument.at("/paths/~1admin~1merchant-owners~1{userId}~1places~1{placeId}~1quality/put");
        assertThat(qualityOperation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantOwnerPlaceQualityUpdateRequest");
        assertThat(qualityOperation.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantOwnerPlaceResponse");

        JsonNode roleRequestSchema = webDocument.at("/components/schemas/AdminRoleAssignmentRequest");
        assertThat(resolveSchema(webDocument, roleRequestSchema.at("/properties/role")).path("enum").toString())
                .contains("SUPER_ADMIN", "CONTENT_MODERATOR", "MERCHANT_OPERATOR", "SUPPORT_OPERATOR", "ANALYST");
        assertThat(roleRequestSchema.at("/properties/reason/maxLength").asInt()).isEqualTo(500);
        JsonNode roleResponseSchema = webDocument.at("/components/schemas/AdminRoleAssignmentResponse");
        assertThat(resolveSchema(webDocument, roleResponseSchema.at("/properties/status")).path("enum").toString())
                .contains("ACTIVE", "REVOKED");
        assertThat(roleResponseSchema.path("properties").has("permissions")).isTrue();

        JsonNode roleListOperation = webDocument.at("/paths/~1admin~1users~1{userId}~1roles/get");
        assertThat(roleListOperation.at("/responses/200/content/*~1*/schema/type").asText())
                .isEqualTo("array");
        assertThat(roleListOperation.path("responses").has("401")).isTrue();
        assertThat(roleListOperation.path("responses").has("403")).isTrue();
        assertThat(roleListOperation.path("responses").has("404")).isTrue();
        assertThat(roleListOperation.path("security").path(0).path("bearerAuth").isArray()).isTrue();

        JsonNode roleAssignOperation = webDocument.at("/paths/~1admin~1users~1{userId}~1roles/post");
        assertThat(roleAssignOperation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminRoleAssignmentRequest");
        for (String status : List.of("400", "401", "403", "404", "409")) {
            assertThat(roleAssignOperation.path("responses").has(status)).isTrue();
        }

        JsonNode roleRevokeOperation = webDocument.at("/paths/~1admin~1users~1{userId}~1roles~1{role}/delete");
        assertThat(roleRevokeOperation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminRoleAssignmentRevokeRequest");
        assertThat(roleRevokeOperation.path("responses").has("401")).isTrue();
        assertThat(roleRevokeOperation.path("responses").has("403")).isTrue();
        assertThat(roleRevokeOperation.path("responses").has("404")).isTrue();

        JsonNode placeInformationGet = appDocument.at("/paths/~1merchant-owner~1places~1{placeId}~1information/get");
        JsonNode placeInformationPut = appDocument.at("/paths/~1merchant-owner~1places~1{placeId}~1information/put");
        assertThat(placeInformationGet.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantPlaceInformationResponse");
        assertThat(placeInformationGet.path("responses").has("401")).isTrue();
        assertThat(placeInformationGet.path("responses").has("403")).isTrue();
        assertThat(placeInformationGet.path("responses").has("404")).isTrue();
        assertThat(placeInformationGet.path("security").path(0).path("bearerAuth").isArray()).isTrue();
        assertThat(placeInformationPut.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantPlaceInformationUpdateRequest");
        assertThat(placeInformationPut.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/MerchantPlaceInformationResponse");
        assertThat(placeInformationPut.path("responses").has("400")).isTrue();
        assertThat(placeInformationPut.path("responses").has("401")).isTrue();
        assertThat(placeInformationPut.path("responses").has("403")).isTrue();
        assertThat(placeInformationPut.path("security").path(0).path("bearerAuth").isArray()).isTrue();
        JsonNode informationRequest = appDocument.at("/components/schemas/MerchantPlaceInformationUpdateRequest");
        assertThat(informationRequest.at("/properties/description/maxLength").asInt()).isEqualTo(1000);
        assertThat(informationRequest.at("/properties/contactPhone/maxLength").asInt()).isEqualTo(30);
        assertThat(informationRequest.at("/properties/websiteUrl/maxLength").asInt()).isEqualTo(500);
        assertThat(informationRequest.at("/properties/reservationUrl/maxLength").asInt()).isEqualTo(500);
        assertThat(informationRequest.at("/properties/websiteUrl/pattern").asText())
                .isEqualTo("^https?://\\S+$");
        assertThat(informationRequest.at("/properties/reservationUrl/pattern").asText())
                .isEqualTo("^https?://\\S+$");

        JsonNode onboardingRequestSchema = webDocument.at("/components/schemas/MerchantOnboardingUpdateRequest");
        List<String> onboardingRequiredFields = new ArrayList<>();
        onboardingRequestSchema.path("required").forEach(field -> onboardingRequiredFields.add(field.asText()));
        assertThat(onboardingRequiredFields).contains("status", "completionRate");
        assertThat(onboardingRequestSchema.at("/properties/completionRate/minimum").asInt()).isZero();
        assertThat(onboardingRequestSchema.at("/properties/completionRate/maximum").asInt()).isEqualTo(100);
        assertThat(onboardingRequestSchema.at("/properties/reason/maxLength").asInt()).isEqualTo(500);

        JsonNode qualityRequestSchema = webDocument.at("/components/schemas/MerchantOwnerPlaceQualityUpdateRequest");
        List<String> qualityRequiredFields = new ArrayList<>();
        qualityRequestSchema.path("required").forEach(field -> qualityRequiredFields.add(field.asText()));
        assertThat(qualityRequiredFields).contains(
                "status",
                "reservationResponseRate",
                "reservationCancellationRate",
                "noShowRate"
        );
        for (String metricField : List.of(
                "reservationResponseRate",
                "reservationCancellationRate",
                "noShowRate"
        )) {
            assertThat(qualityRequestSchema.at("/properties/" + metricField + "/minimum").asInt()).isZero();
            assertThat(qualityRequestSchema.at("/properties/" + metricField + "/maximum").asInt()).isEqualTo(100);
        }
        assertThat(qualityRequestSchema.at("/properties/reason/maxLength").asInt()).isEqualTo(500);
        assertNullableProperty(webDocument, "MerchantOwnerPlaceResponse", "qualityEvaluatedAt");
    }

    @Test
    void touristOfferAndCouponApisAreExposedInAppGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

        for (String path : List.of(
                "/offers",
                "/offers/{offerId}",
                "/offers/{offerId}/coupons",
                "/coupons",
                "/merchant-owner/offers",
                "/merchant-owner/offers/{offerId}",
                "/merchant-owner/offers/{offerId}/publish",
                "/merchant-owner/offers/{offerId}/close",
                "/merchant-owner/offers/coupons/redeem"
        )) {
            assertThat(appDocument.path("paths").has(path)).isTrue();
            assertThat(webDocument.path("paths").has(path)).isFalse();
        }
        assertThat(appDocument.path("components").path("schemas").has("OfferCreateRequest")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("OfferResponse")).isTrue();
        assertThat(appDocument.path("components").path("schemas").has("CouponResponse")).isTrue();
        assertThat(appDocument.at("/paths/~1offers~1{offerId}~1coupons/post/responses/409/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/ErrorResponse");
        assertThat(appDocument.at("/paths/~1merchant-owner~1offers~1coupons~1redeem/post/requestBody/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/CouponRedeemRequest");
        for (String operationPath : List.of(
                "/paths/~1offers/get",
                "/paths/~1offers~1{offerId}/get",
                "/paths/~1offers~1{offerId}~1coupons/post",
                "/paths/~1coupons/get",
                "/paths/~1merchant-owner~1offers/get",
                "/paths/~1merchant-owner~1offers/post",
                "/paths/~1merchant-owner~1offers~1{offerId}/get",
                "/paths/~1merchant-owner~1offers~1{offerId}~1publish/post",
                "/paths/~1merchant-owner~1offers~1{offerId}~1close/post",
                "/paths/~1merchant-owner~1offers~1coupons~1redeem/post"
        )) {
            JsonNode operation = appDocument.at(operationPath);
            assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
            assertThat(operation.at("/responses/401/content/*~1*/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/ErrorResponse");
        }
        assertThat(appDocument.at("/paths/~1offers~1{offerId}/get/responses/200/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/OfferResponse");
        assertThat(appDocument.at("/paths/~1merchant-owner~1offers~1{offerId}/get/responses/200/content/*~1*/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/OfferResponse");
        assertNullableProperty(appDocument, "CouponResponse", "redeemedAt");
        assertThat(appDocument.at("/components/schemas/CouponRedeemRequest/properties/code/pattern").asText())
                .isEqualTo("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

        JsonNode createRequestSchema = appDocument.at("/components/schemas/OfferCreateRequest");
        List<String> requiredFields = new ArrayList<>();
        createRequestSchema.path("required").forEach(field -> requiredFields.add(field.asText()));
        assertThat(requiredFields).contains(
                "placeId",
                "title",
                "description",
                "benefitDescription",
                "startsAt",
                "endsAt",
                "couponValidityDays"
        );
        assertNullableProperty(appDocument, "OfferCreateRequest", "totalQuantity");
        assertNullableProperty(appDocument, "OfferResponse", "totalQuantity");
        assertNullableProperty(appDocument, "OfferResponse", "remainingQuantity");
        assertThat(appDocument.at("/components/schemas/OfferCreateRequest/properties/eligibilityPolicy/enum"))
                .extracting(JsonNode::asText)
                .containsExactly("ACTIVE_TRAVEL_SCHEDULE", "PUBLIC");
        assertThat(appDocument.at("/components/schemas/OfferCreateRequest/properties/inventoryPolicy/enum"))
                .extracting(JsonNode::asText)
                .containsExactly("LIMITED", "UNLIMITED");
        assertThat(appDocument.at("/components/schemas/OfferCreateRequest/properties/expiryPolicy/enum"))
                .extracting(JsonNode::asText)
                .containsExactly(
                        "ISSUE_PLUS_DAYS_CAPPED_BY_OFFER_END",
                        "ISSUE_PLUS_DAYS",
                        "OFFER_END"
                );
        for (String property : List.of("title", "description", "benefitDescription")) {
            assertThat(createRequestSchema.path("properties").path(property).path("minLength").asInt())
                    .isEqualTo(1);
        }
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
    void adminPlacePostVisibilityContractIsDocumented() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");
        JsonNode postSchema = webDocument.at("/components/schemas/AdminMapPlaceImageItem");

        assertThat(resolveSchema(webDocument, postSchema.at("/properties/visibilityStatus")).path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
        assertNullableProperty(webDocument, "AdminMapPlaceImageItem", "hiddenReason");
    }

    @Test
    void adminPlaceGrowthPhotoCountsAreDocumented() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");
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
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

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
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

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
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

        assertThat(appDocument.path("paths").has("/events")).isTrue();
        assertThat(appDocument.path("paths").has("/admin/place-events")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/place-events")).isTrue();
    }

    @Test
    void placeInformationReportDisputeContractsAreSeparatedIntoAppAndWebGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/web");

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

    private void assertLimitParameter(JsonNode parameter) {
        assertThat(parameter.path("name").asText()).isEqualTo("limit");
        assertThat(parameter.path("in").asText()).isEqualTo("query");
        assertThat(parameter.path("required").asBoolean()).isFalse();
        assertThat(parameter.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(10);
        assertThat(parameter.path("schema").path("minimum").asInt()).isEqualTo(1);
        assertThat(parameter.path("schema").path("maximum").asInt()).isEqualTo(50);
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
