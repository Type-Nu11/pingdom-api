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
import java.util.Set;
import java.util.TreeSet;
import com.typenull.pingdom.shared.config.swagger.ApiAudience.Group;
import com.typenull.pingdom.shared.config.swagger.SwaggerTagCatalog;
import org.springframework.core.io.ClassPathResource;
import java.util.Map.Entry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 생성된 OpenAPI의 그룹·인증·스키마·예시를 검증한다. 내장 예시 검사기는 JSON Schema 전체 규칙을 구현하지 않는다.
 */
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

    /**
     * 여섯 문서의 요청·응답 예시를 순회해 helper가 지원하는 타입·enum·필수 필드 불일치를 모아 확인한다. 전체 JSON Schema 규격 검증은 아니다.
     */
    @Test
    void examplesMatchSchemas() throws Exception {
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

    /**
     * 통합 문서의 bearerAuth가 HTTP Bearer 및 JWT 형식으로 선언됐는지 확인한다.
     */
    @Test
    void jwtSecurityScheme() throws Exception {
        JsonNode document = readApiDocs("/v3/api-docs");
        JsonNode bearerAuth = document.path("components").path("securitySchemes").path("bearerAuth");

        assertThat(bearerAuth.path("type").asText()).isEqualTo("http");
        assertThat(bearerAuth.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerAuth.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    /**
     * 가용량·리뷰·입지 분석의 인증 오류와 FCM 등록의 일반·검증 오류 oneOf 계약을 확인한다.
     */
    @Test
    void groupAuthorizationContracts() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode availability = appDocument.at("/paths/~1places~1{placeId}~1availabilities/get");

        assertThat(availability.at("/security/0/bearerAuth").isArray()).isTrue();
        assertErrorResponse(availability, "401");
        assertErrorResponse(availability, "403");

        JsonNode commonDocument = readApiDocs("/v3/api-docs/common");
        JsonNode reviews = commonDocument.at("/paths/~1places~1{placeId}~1reviews/get");

        assertThat(reviews.at("/security/0/bearerAuth").isArray()).isTrue();
        assertErrorResponse(reviews, "401");

        JsonNode protectedAnalysis = appDocument.at("/paths/~1analysis~1reports~1location/post");
        assertThat(protectedAnalysis.at("/security/0/bearerAuth").isArray()).isTrue();
        assertErrorResponse(protectedAnalysis, "401");
        assertErrorResponse(protectedAnalysis, "403");

        JsonNode fcmRegistration = appDocument.at("/paths/~1firebase~1fcm-tokens/post");
        assertThat(fcmRegistration.path("responses").has("400")).isTrue();
        assertThat(fcmRegistration.at("/responses/400/content/application~1json/schema/oneOf").toString())
                .contains("ErrorResponse", "ValidationErrorResponse");
    }

    /**
     * 공개 경로 목록을 제외한 모든 문서 operation이 Bearer 및 공통 401·403 오류 스키마를 선언하는지 확인한다.
     */
    @Test
    void protectedOperationSecurity() throws Exception {
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

    /**
     * 각 그룹의 operation과 태그를 baseline에 비교하고 허용 기능 분류 하나 및 태그 순서·설명을 확인한다.
     */
    @Test
    void audienceAndSectionContracts() throws Exception {
        for (Group audience : Group.values()) {
            String docPath = "/v3/api-docs/" + audience.documentName();
            JsonNode document = readApiDocs(docPath);
            JsonNode baseline;
            try (var input = new ClassPathResource("openapi-baseline/" + audience.documentName() + ".json").getInputStream()) {
                baseline = objectMapper.readTree(input);
            }
            assertThat(operationKeys(document)).as("%s 소속 operation 집합", docPath)
                    .isEqualTo(operationKeys(baseline));
            List<String> allowed = SwaggerTagCatalog.sections(audience).stream()
                    .map(SwaggerTagCatalog.Section::name).toList();
            Set<String> used = new TreeSet<>();
            document.path("paths").fields().forEachRemaining(path ->
                    path.getValue().fields().forEachRemaining(entry -> {
                        if (isHttpMethod(entry.getKey())) {
                            JsonNode tags = entry.getValue().path("tags");
                            assertThat(tags.size()).as("%s %s %s 단일 분류", docPath, entry.getKey(), path.getKey()).isEqualTo(1);
                            assertThat(tags.get(0).asText()).isIn(allowed);
                            assertThat(tags).isEqualTo(baseline.path("paths").path(path.getKey()).path(entry.getKey()).path("tags"));
                            used.add(tags.get(0).asText());
                        }
                    }));
            List<String> documentedTags = new ArrayList<>();
            document.path("tags").forEach(tag -> {
                documentedTags.add(tag.path("name").asText());
                assertThat(tag.path("description").asText()).isNotBlank();
            });
            assertThat(documentedTags).containsExactlyElementsOf(allowed.stream().filter(used::contains).toList());
            assertThat(document.path("paths").has("/voice-ai/sessions")).isFalse();
        }
    }

    /**
     * path 수준 메타데이터를 제외하고 HTTP 메서드와 경로 조합을 정렬된 집합으로 만든다.
     */
    private Set<String> operationKeys(JsonNode document) {
        Set<String> keys = new TreeSet<>();
        document.path("paths").fields().forEachRemaining(path ->
                path.getValue().fieldNames().forEachRemaining(method -> {
                    if (isHttpMethod(method)) {
                        keys.add(method + " " + path.getKey());
                    }
                }));
        return keys;
    }

    /**
     * 최근 활동·대기 항목이 admin에만 있고 operation ID·응답 스키마·limit 범위·nullable 필드가 기대 계약인지 확인한다.
     */
    @Test
    void adminDashboardContracts() throws Exception {
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

    /**
     * 탐색과 추천의 필터·limit 상한 100/20·오류 예시 및 영업·혜택·예약·추천 사유 필드를 확인한다.
     */
    @Test
    void discoveryRecommendationContracts() throws Exception {
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

    /**
     * 관리자 장소의 표준 카테고리·한글 이름·nullable 분류·필수 레벨과 빈 페이지 최소 페이지 수를 확인한다.
     */
    @Test
    void adminPlaceCategoryContract() throws Exception {
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

    /**
     * app 체크인 생성의 요청·201 응답과 명시된 여섯 실패 상태의 공통 오류 스키마를 확인한다.
     */
    @Test
    void checkInContract() throws Exception {
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

    /**
     * 증빙 업로드의 multipart binary·인증·201·실패 응답과 증빙 파일 조회의 byte 형식을 확인한다.
     */
    @Test
    void visitEvidenceContract() throws Exception {
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

    /**
     * scout 사용자 제출과 관리자 검토 경로의 그룹 분리·요청/응답 참조·제보 유형 enum을 확인한다.
     */
    @Test
    void scoutReportGroups() throws Exception {
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

    /**
     * app 문서에서 구형 단수 FCM·장소·사용자 북마크 경로가 제거됐는지 확인한다.
     */
    @Test
    void removedAliases() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");

        assertThat(appDocument.path("paths").has("/firebase/fcm-token")).isFalse();
        assertThat(appDocument.path("paths").has("/place")).isFalse();
        assertThat(appDocument.path("paths").has("/users/bookmarks")).isFalse();
    }

    /**
     * 여행 목적 GET/PUT 스키마와 업데이트 요청의 travelPurposes 필수 표시를 확인한다.
     */
    @Test
    void travelPurposeContract() throws Exception {
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

    /**
     * 여행 일정·취소·현재 활동 의도 경로 및 요청/응답 스키마와 일정 생성·변경의 오류 상태를 확인한다.
     */
    @Test
    void travelIntentContracts() throws Exception {
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

    /**
     * 상점 소유자·장소 신청·첨부 경로는 merchant에, 운영 심사 경로는 admin에 포함하고 구형 app 경로는 제외하는지 확인한다.
     */
    @Test
    void merchantRegistrationGroups() throws Exception {
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

    /**
     * 상점 소유자 승인 요청 스키마에 reason이 있고 placeIds는 없는지 확인한다. 나머지 필드 전체를 제한하지는 않는다.
     */
    @Test
    void merchantReviewReason() throws Exception {
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode reviewRequest = resolveSchema(
                adminDocument,
                adminDocument.at("/paths/~1admin~1merchant-owners~1{userId}~1approve/post/requestBody"
                        + "/content/application~1json/schema")
        );

        assertThat(reviewRequest.path("properties").has("reason")).isTrue();
        assertThat(reviewRequest.path("properties").has("placeIds")).isFalse();
    }

    /**
     * 신청 상태가 반복 query 배열(form/explode)로 문서화되고 유형·검색어·제출 기간 필터가 제공되는지 확인한다.
     */
    @Test
    void applicationFilterContract() throws Exception {
        JsonNode adminDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode operation = adminDocument.at("/paths/~1admin~1merchant-place-applications/get");
        JsonNode statusParameter = parameter(operation, "status");
        JsonNode applicationTypeParameter = parameter(operation, "applicationType");
        JsonNode keywordParameter = parameter(operation, "keyword");
        JsonNode submittedFromParameter = parameter(operation, "submittedFrom");
        JsonNode submittedToParameter = parameter(operation, "submittedTo");

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
        assertThat(keywordParameter.path("description").asText()).contains("username", "공백");
        assertThat(submittedFromParameter.path("schema").path("format").asText()).isEqualTo("date-time");
        assertThat(submittedToParameter.path("schema").path("format").asText()).isEqualTo("date-time");
    }

    /**
     * 구형 추천·지도·상점 심사·광고 경로 및 관리자 알림 사용자 ID, 구형 제재 기간 파라미터가 문서에서 빠졌는지 확인한다.
     */
    @Test
    void removedLegacyContracts() throws Exception {
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

    /**
     * 관광객 혜택·쿠폰 경로와 상점 혜택 관리·사용 처리 경로가 서로의 그룹에 노출되지 않는지 확인한다.
     */
    @Test
    void offerCouponGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");

        for (String path : List.of("/offers", "/offers/{offerId}", "/offers/{offerId}/coupons", "/coupons", "/coupons/{couponId}")) {
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

    /**
     * 상점 혜택 목록의 장소 ID 타입·상태 enum과 소유 범위·빈 목록 설명을 확인한다.
     */
    @Test
    void merchantOfferFilters() throws Exception {
        JsonNode merchantDocument = readApiDocs("/v3/api-docs/merchant");
        JsonNode operation = merchantDocument.at("/paths/~1merchant-owner~1offers/get");
        JsonNode placeIdParameter = parameter(operation, "placeId");
        JsonNode statusParameter = parameter(operation, "status");

        assertThat(placeIdParameter.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(statusParameter.path("schema").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "CLOSED");
        assertThat(operation.path("description").asText()).contains("소유", "빈 목록");
    }

    /**
     * 관광 정보 요청·응답의 영문명·요약 및 자동완성 영문명이 null을 허용하는지 확인한다.
     */
    @Test
    void nullableTouristStrings() throws Exception {
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

    /**
     * 관리자 장소 게시물의 표시 상태는 VISIBLE/HIDDEN이고 숨김 사유는 nullable인지 확인한다.
     */
    @Test
    void postVisibilityContract() throws Exception {
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");
        JsonNode postSchema = webDocument.at("/components/schemas/AdminMapPlaceImageItem");

        assertThat(resolveSchema(webDocument, postSchema.at("/properties/visibilityStatus")).path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("VISIBLE", "HIDDEN");
        assertNullableProperty(webDocument, "AdminMapPlaceImageItem", "hiddenReason");
    }

    /**
     * 성장 응답 참조와 노출·숨김 사진의 성장 반영 여부를 구분한 설명을 확인한다.
     */
    @Test
    void growthPhotoCountContract() throws Exception {
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

    /**
     * 탐색 정렬·관광 카테고리·거리와 클릭 필수 필드, 관리자 노출 상태 변경의 그룹·enum·사유 필수 계약을 확인한다.
     */
    @Test
    void discoveryFilterSortContract() throws Exception {
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

    /**
     * 관광객 카드의 필수 int64 장소 ID, 정상·404 응답과 영업·출처·검증 요약 필드를 확인한다.
     */
    @Test
    void touristCardContract() throws Exception {
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

    /**
     * 방문 결정 문서의 장소 ID·응답·오류 스키마 및 행사·가용량·혜택·판정 시각 필드를 확인한다.
     */
    @Test
    void visitDecisionContract() throws Exception {
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

    /**
     * 방문 결정 경로가 app에는 있고 admin에는 없는지 확인한다.
     */
    @Test
    void visitDecisionGroup() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/places/{placeId}/visit-decision")).isTrue();
        assertThat(webDocument.path("paths").has("/places/{placeId}/visit-decision")).isFalse();
    }

    /**
     * 관광객용 상점 정보 스키마는 설명·예약 URL·갱신 시각을 노출하고 편집자 ID는 제외하는지 확인한다.
     */
    @Test
    void merchantEditorPrivacy() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode merchantSchema = appDocument.at(
                "/components/schemas/PlaceVisitDecisionMerchantInformationResponse/properties"
        );

        assertThat(merchantSchema.has("description")).isTrue();
        assertThat(merchantSchema.has("reservationUrl")).isTrue();
        assertThat(merchantSchema.has("updatedAt")).isTrue();
        assertThat(merchantSchema.has("updatedByUserId")).isFalse();
    }

    /**
     * 지도·카드·방문 결정·공지·검증 사진·추천 설명·전환 문서의 인증·범위·필수/nullable 필드를 확인한다.
     */
    @Test
    void explorationContracts() throws Exception {
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
        assertThat(appDocument.at("/components/schemas/MapLinkConversionRequest/properties/provider/example").asText())
                .isEqualTo("NAVER");

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

    /**
     * 정기 영업시간·날짜 예외의 배열/불리언 구조, 시간 문자열 형식과 변경 실패 응답을 확인한다.
     */
    @Test
    void operatingScheduleContract() throws Exception {
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

    /**
     * 행사 공개 조회와 관리자 작성·게시·취소 경로의 스키마, 필수 필드 수와 오류 계약을 확인한다.
     */
    @Test
    void periodEventContract() throws Exception {
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

    /**
     * app 행사 조회 문서와 admin 행사 관리 문서가 분리되는지 확인한다.
     */
    @Test
    void periodEventGroups() throws Exception {
        JsonNode appDocument = readApiDocs("/v3/api-docs/app");
        JsonNode webDocument = readApiDocs("/v3/api-docs/admin");

        assertThat(appDocument.path("paths").has("/events")).isTrue();
        assertThat(appDocument.path("paths").has("/admin/place-events")).isFalse();
        assertThat(webDocument.path("paths").has("/admin/place-events")).isTrue();
    }

    /**
     * 정보 신고·반박 사용자 경로와 관리자 검토 경로를 분리하고 대상·사유·처리 상태 enum을 확인한다.
     */
    @Test
    void informationDisputeGroups() throws Exception {
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

    /**
     * 알림 설정 요청·응답의 방해 금지 시작/종료가 time 형식 문자열인지 확인한다.
     */
    @Test
    void quietHoursContract() throws Exception {
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

    /**
     * 연쇄 내부 참조의 JSON Pointer에서 ~1과 ~0을 복원해 최종 객체 스키마를 찾는지 확인한다.
     */
    @Test
    void nestedSchemaReferences() throws Exception {
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

    /**
     * operation 존재·Bearer·성공 상태와 지정 실패 상태의 공통 오류 참조를 확인한다.
     */
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

    /**
     * 이름으로 찾은 파라미터 스키마의 최솟값과 최댓값을 비교한다.
     */
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

    /**
     * MockMvc 응답 본문을 UTF-8 JSON으로 읽는다. HTTP 상태 assertion은 이 helper에 포함하지 않는다.
     */
    private JsonNode readApiDocs(String apiDocPath) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(apiDocPath))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    /**
     * 지정 속성의 존재와 nullable=true 선언을 함께 확인한다.
     */
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

    /**
     * 이름이 같은 첫 파라미터를 반환하고 누락 시 MissingNode를 반환한다.
     */
    private JsonNode parameter(JsonNode operation, String parameterName) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameterName.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        return objectMapper.missingNode();
    }

    /**
     * required 배열의 선언 순서를 유지한 필드 이름 목록을 만든다.
     */
    private List<String> requiredFields(JsonNode schema) {
        List<String> fields = new ArrayList<>();
        schema.path("required").forEach(field -> fields.add(field.asText()));
        return fields;
    }

    /**
     * 각 미디어 타입의 단일 example과 이름별 examples.value를 검증하고 오류 위치를 함께 누적한다. content가 없으면 생략한다.
     */
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

    /**
     * 내부 참조를 해석해 enum·복합 스키마·기본 타입을 검사한다. null은 생략하고 oneOf는 하나 이상 일치하면 통과하므로 배타성·범위·format은 보장하지 않는다.
     */
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

    /**
     * 객체 타입과 required 이름의 존재를 확인하고 null이 아닌 선언 속성을 재귀 검사한다. 추가 속성 및 null 허용 여부는 검사하지 않는다.
     */
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

    /**
     * 배열 타입을 확인한 뒤 인덱스를 오류 경로에 붙여 모든 원소를 items 스키마로 검사한다.
     */
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

    /**
     * path 항목 중 OpenAPI의 HTTP operation으로 취급할 메서드 이름을 판별한다.
     */
    private boolean isHttpMethod(String method) {
        return switch (method) {
            case "get", "post", "put", "patch", "delete", "head", "options", "trace" -> true;
            default -> false;
        };
    }

    /**
     * 이 검증에서 공개로 간주하는 루트·auth 하위·상담 intro를 제외한다. 런타임 보안 설정을 읽는 메서드는 아니다.
     */
    private boolean isPublicPath(String path) {
        return "/".equals(path)
                || path.startsWith("/auth/")
                || "/consultations/intro".equals(path);
    }

    /**
     * 대시보드 limit이 선택 query 정수이며 기본 10, 범위 1~50으로 문서화됐는지 확인한다.
     */
    private void assertLimitParameter(JsonNode parameter) {
        assertThat(parameter.path("name").asText()).isEqualTo("limit");
        assertThat(parameter.path("in").asText()).isEqualTo("query");
        assertThat(parameter.path("required").asBoolean()).isFalse();
        assertThat(parameter.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(parameter.path("schema").path("default").asInt()).isEqualTo(10);
        assertThat(parameter.path("schema").path("minimum").asInt()).isEqualTo(1);
        assertThat(parameter.path("schema").path("maximum").asInt()).isEqualTo(50);
    }

    /**
     * 응답 상태가 존재하고 application/json 또는 와일드카드 미디어 타입이 ErrorResponse를 참조하는지 확인한다.
     */
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

    /**
     * 내부 JSON Pointer 참조를 반복 해석하되 외부 참조·누락·직접 자기 참조에서 멈춘다. 여러 스키마 사이의 순환을 별도로 탐지하지 않는다.
     */
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
