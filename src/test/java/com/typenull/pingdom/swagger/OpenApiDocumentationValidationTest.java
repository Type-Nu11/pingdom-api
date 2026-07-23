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
        assertLimitParameter(recentActivities.path("parameters").path(0));

        assertThat(pendingItems.path("operationId").asText()).isEqualTo("getPendingItems");
        assertThat(pendingItems.at("/responses/200/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AdminDashboardPendingItemsResponse");
        assertThat(pendingItems.path("responses").has("401")).isTrue();
        assertThat(pendingItems.path("responses").has("403")).isTrue();
        assertLimitParameter(pendingItems.path("parameters").path(0));

        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentPlaceItem")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentPostItem")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentReportItem")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardRecentUserSanctionItem")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminDashboardPendingItem")).isTrue();
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
        assertThat(webDocument.path("components").path("schemas").has("MerchantOnboardingUpdateRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("MerchantOwnerPlaceQualityUpdateRequest")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("MerchantOwnerPlaceResponse")).isTrue();
        assertThat(webDocument.path("components").path("schemas").has("AdminMerchantPlaceClaimResponse")).isTrue();
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
                "totalQuantity",
                "couponValidityDays"
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
        assertThat(resolveSchema(webDocument, discoveryRequestSchema.at("/properties/discoveryStatus"))
                .path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
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
        assertThat(resolveSchema(appDocument, cardSchema.at("/properties/operatingStatus"))
                .path("enum")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("OPERATING", "TEMPORARILY_CLOSED", "PERMANENTLY_CLOSED");
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
