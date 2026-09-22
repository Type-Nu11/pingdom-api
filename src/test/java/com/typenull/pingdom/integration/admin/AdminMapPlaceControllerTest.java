package com.typenull.pingdom.integration.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.moderation.domain.AdminPlaceSortParam;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminPlaceMergeHistoryRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendEvent;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.category.PlaceCategory;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationExposure;
import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrafficPolicyRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceInformationEvidenceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.verification.domain.LocationCheckIn;
import com.typenull.pingdom.verification.infrastructure.LocationCheckInRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AdminMapPlaceControllerTest {

    private static final AtomicInteger ADMIN_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private LocationCheckInRepository locationCheckInRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;

    @Autowired
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Autowired
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Autowired
    private PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Autowired
    private PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;

    @Autowired
    private PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;

    @Autowired
    private PlaceRecommendationTrafficPolicyRepository placeRecommendationTrafficPolicyRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PlaceInformationEvidenceRepository placeInformationEvidenceRepository;

    @Autowired
    private AdminPlaceMergeHistoryRepository adminPlaceMergeHistoryRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    /** Outbox·감사·추천·게시글·체크인·근거와 영업 일정 등을 참조 순서대로 비운 뒤 장소와 사용자를 삭제해 테스트 간 DB 상태를 격리. */
    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        adminAuditLogRepository.deleteAllInBatch();
        mapBookmarkTrendEventRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        placeRecommendationConversionRepository.deleteAllInBatch();
        placeRecommendationClickRepository.deleteAllInBatch();
        placeRecommendationExposureRepository.deleteAllInBatch();
        placeRecommendationFeatureLogRepository.deleteAllInBatch();
        placeRecommendationVersionSnapshotRepository.deleteAllInBatch();
        placeSimilaritySnapshotRepository.deleteAllInBatch();
        placeRecommendationTrafficPolicyRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        adminPlaceMergeHistoryRepository.deleteAllInBatch();
        locationCheckInRepository.deleteAllInBatch();
        placeInformationEvidenceRepository.deleteAllInBatch();
        clearOperatingScheduleRows();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /** 각 테스트 뒤 영업시간 자식 행을 제거해 뒤따르는 장소 정리에 외래 키 잔여가 남지 않게 함. */
    @AfterEach
    void tearDownOperatingScheduleRows() {
        clearOperatingScheduleRows();
    }

    /** 예외 시간→예외 날짜→정기 영업시간 순서로 테스트 DB의 해당 테이블 전체를 비움. */
    private void clearOperatingScheduleRows() {
        jdbcTemplate.update("DELETE FROM map_place_operating_exception_hour");
        jdbcTemplate.update("DELETE FROM map_place_operating_exception");
        jdbcTemplate.update("DELETE FROM map_place_regular_operating_hour");
    }

    /** 관리자 목록에서 영문명 키워드로 저장 장소를 찾고 정규 카테고리·관광 정보·성장 레벨·페이지 메타데이터를 반환하는지 확인. */
    @Test
    void listPlacesReturnsRegisteredPlaces() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .englishName("Jinju Castle")
                .address("경상남도 진주시 남강로 626")
                .category("문화재")
                .touristSummary("남강을 내려다보는 역사 유적")
                .touristCategories(Set.of(TouristCategory.EXHIBITION, TouristCategory.OTHER))
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(11L)
                .registrant("placeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "jinju castle")
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("진주성"))
                .andExpect(jsonPath("$.places[0].address").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.places[0].discoveryStatus").value("VISIBLE"))
                .andExpect(jsonPath("$.places[0].category").value("CULTURAL_HERITAGE"))
                .andExpect(jsonPath("$.places[0].categoryName").value("문화재"))
                .andExpect(jsonPath("$.places[0].englishName").value("Jinju Castle"))
                .andExpect(jsonPath("$.places[0].touristSummary").value("남강을 내려다보는 역사 유적"))
                .andExpect(jsonPath("$.places[0].touristCategories", containsInAnyOrder("EXHIBITION", "OTHER")))
                .andExpect(jsonPath("$.places[0].level").value(1))
                .andExpect(jsonPath("$.places[0].placeGrowth.level").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /** 한국어 문화재로 저장된 장소가 정규 CULTURAL_HERITAGE 필터에 포함되고 카페는 제외되는지 확인. */
    @Test
    void listPlacesFiltersByCategory() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace culturalHeritagePlace = mapPlaceRepository.save(MapPlace.builder()
                .name("문화재 카테고리 장소")
                .address("경상남도 진주시 문화재로 1")
                .category("문화재")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(31L)
                .registrant("tourismRegistrar")
                .build());
        mapPlaceRepository.save(MapPlace.builder()
                .name("카페 카테고리 장소")
                .address("경상남도 진주시 카페로 1")
                .category("CAFE")
                .latitude(35.1895)
                .longitude(128.0790)
                .userId(32L)
                .registrant("cafeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("category", "CULTURAL_HERITAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(culturalHeritagePlace.getId()))
                .andExpect(jsonPath("$.places[0].category").value("CULTURAL_HERITAGE"))
                .andExpect(jsonPath("$.places[0].categoryName").value("문화재"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    /** 공백·소문자 카테고리와 키워드·오래된 순·페이지 조건을 함께 적용해 카페 두 건 중 두 번째만 반환하는지 확인. */
    @Test
    void combinesPlaceListFilters() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("복합검색 카페 A")
                .address("경상남도 진주시 복합검색로 1")
                .category("CAFE")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(41L)
                .registrant("firstCafeRegistrar")
                .build());
        MapPlace secondCafe = mapPlaceRepository.save(MapPlace.builder()
                .name("복합검색 카페 B")
                .address("경상남도 진주시 복합검색로 2")
                .category("CAFE")
                .latitude(35.1895)
                .longitude(128.0790)
                .userId(42L)
                .registrant("secondCafeRegistrar")
                .build());
        mapPlaceRepository.save(MapPlace.builder()
                .name("복합검색 식당")
                .address("경상남도 진주시 복합검색로 3")
                .category("RESTAURANT")
                .latitude(35.1896)
                .longitude(128.0791)
                .userId(43L)
                .registrant("restaurantRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("category", " cafe ")
                        .param("keyword", "복합검색")
                        .param("sortParam", AdminPlaceSortParam.OLDEST.name())
                        .param("page", "2")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(secondCafe.getId()))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /** 일치하지 않는 카테고리 조회는 빈 목록·총 0건·총 1페이지·다음 페이지 없음으로 응답하는지 확인. */
    @Test
    void returnsEmptyCategoryPage() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("카페 장소")
                .address("경상남도 진주시 카페로 10")
                .category("CAFE")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(51L)
                .registrant("cafeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("category", "숙박"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /** 카테고리 없는 장소를 null 코드·미분류 표시명·기본 레벨로 조회하는지 확인. */
    @Test
    void listsMissingCategoryAsUncategorized() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("미분류 장소")
                .address("경상남도 진주시 미분류로 1")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(12L)
                .registrant("placeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].category").value(nullValue()))
                .andExpect(jsonPath("$.places[0].categoryName").value("미분류"))
                .andExpect(jsonPath("$.places[0].level").value(1));
    }

    /** OTHER로 저장한 장소가 OTHER 코드와 기타 표시명을 유지하는지 확인. */
    @Test
    void labelsOtherCategoryExplicitly() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("기타 장소")
                .address("경상남도 진주시 기타로 1")
                .category("OTHER")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(14L)
                .registrant("placeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].category").value("OTHER"))
                .andExpect(jsonPath("$.places[0].categoryName").value("기타"));
    }

    /** 지원하지 않는 기존 자유 문자열 카테고리는 목록 응답에서 null 코드와 미분류 표시명으로 나타나는지 확인. */
    @Test
    void listsUnknownCategoryAsUncategorized() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("기존 자유 문자열 장소")
                .address("경상남도 진주시 레거시로 1")
                .category("legacy-free-text")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(15L)
                .registrant("legacyRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "기존 자유 문자열 장소"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].category").value(nullValue()))
                .andExpect(jsonPath("$.places[0].categoryName").value("미분류"));
    }

    /** 사진 수로 계산한 레벨 내림차순을 확인하고 레벨이 같은 두 장소는 나중 ID가 먼저 오는지 검사. */
    @Test
    void sortsPlacesByDescendingLevel() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace firstHighLevelPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("고레벨 장소 A")
                .address("경상남도 진주시 고레벨로 1")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(21L)
                .registrant("firstHighRegistrar")
                .photoCount(10L)
                .build());
        MapPlace lowLevelPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("저레벨 장소")
                .address("경상남도 진주시 저레벨로 1")
                .latitude(35.1895)
                .longitude(128.0790)
                .userId(22L)
                .registrant("lowRegistrar")
                .photoCount(0L)
                .build());
        MapPlace secondHighLevelPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("고레벨 장소 B")
                .address("경상남도 진주시 고레벨로 2")
                .latitude(35.1896)
                .longitude(128.0791)
                .userId(23L)
                .registrant("secondHighRegistrar")
                .photoCount(10L)
                .build());
        MapPlace middleLevelPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("중레벨 장소")
                .address("경상남도 진주시 중레벨로 1")
                .latitude(35.1897)
                .longitude(128.0792)
                .userId(24L)
                .registrant("middleRegistrar")
                .photoCount(3L)
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortParam", AdminPlaceSortParam.LEVEL_DESC.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].id").value(secondHighLevelPlace.getId()))
                .andExpect(jsonPath("$.places[0].level").value(5))
                .andExpect(jsonPath("$.places[0].placeGrowth.level").value(5))
                .andExpect(jsonPath("$.places[1].id").value(firstHighLevelPlace.getId()))
                .andExpect(jsonPath("$.places[1].placeGrowth.level").value(5))
                .andExpect(jsonPath("$.places[2].id").value(middleLevelPlace.getId()))
                .andExpect(jsonPath("$.places[2].placeGrowth.level").value(3))
                .andExpect(jsonPath("$.places[3].id").value(lowLevelPlace.getId()))
                .andExpect(jsonPath("$.places[3].level").value(1))
                .andExpect(jsonPath("$.places[3].placeGrowth.level").value(1));
    }

    /** 카테고리·대표 이미지가 없는 상세 조회에서 null 값·미분류 표시명·레벨 1을 반환하는지 확인. */
    @Test
    void defaultsMissingPlaceDetailFields() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("미분류 상세 장소")
                .address("경상남도 진주시 미분류로 2")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(13L)
                .registrant("placeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value(nullValue()))
                .andExpect(jsonPath("$.categoryName").value("미분류"))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.level").value(1));
    }

    /** 대표 이미지를 첫 값·수정 값·null로 저장할 때마다 상세 응답이 현재 값을 반영하는지 확인. */
    @Test
    void reflectsRepresentativeImageChanges() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("대표 이미지 갱신 장소")
                .address("경상남도 진주시 대표이미지로 1")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(14L)
                .registrant("placeRegistrar")
                .build());

        mapPlace.updateImageUrl("https://example.com/places/representative-first.jpg");
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/places/representative-first.jpg"));

        mapPlace.updateImageUrl("https://example.com/places/representative-updated.jpg");
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/places/representative-updated.jpg"));

        mapPlace.updateImageUrl(null);
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(nullValue()));
    }

    /** 장소 목록에 게시글용 MOST_LIKED 정렬을 보내면 전용 미지원 정렬 오류로 거부하는지 확인. */
    @Test
    void listPlacesRejectsMostLikedSort() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortParam", SortParam.MOST_LIKED.name()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PLACE_SORT_PARAM"));
    }

    /** 주소 일부와 등록자 숫자 ID 각각으로 검색해 대응하는 장소 한 건만 조회되는지 확인. */
    @Test
    void searchesAddressAndRegistrantId() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace matchingPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .address("경상남도 진주시 남강로 626")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(77L)
                .registrant("placeRegistrar")
                .build());

        mapPlaceRepository.save(MapPlace.builder()
                .name("다른 장소")
                .address("서울특별시 강남구 테헤란로")
                .latitude(37.4981)
                .longitude(127.0276)
                .userId(88L)
                .registrant("anotherRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "남강로 626"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(matchingPlace.getId()))
                .andExpect(jsonPath("$.totalCount").value(1));

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(matchingPlace.getId()))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    /** 저장된 추천 feature 로그를 요청 ID로 조회해 장소·사용자·버전·실험 단계·후보 출처·순위·혜택 점수를 반환하는지 확인. */
    @Test
    void returnsAdminRecommendationExplanation() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("관리자 설명 장소")
                .address("경상남도 진주시 관리자설명로 1")
                .category("카페")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(31L)
                .registrant("placeRegistrar")
                .build());
        placeRecommendationFeatureLogRepository.save(PlaceRecommendationFeatureLog.builder()
                .requestId("admin-request-1")
                .userId(31L)
                .placeId(mapPlace.getId())
                .recommendationVersion("place-rec-v2")
                .recommendationStage(RecommendationStage.EXPERIMENTAL)
                .candidateSource(PlaceRecommendationCandidateSource.PERSONAL)
                .ranking(1)
                .distanceMeters(144)
                .geoScore(0.9d)
                .personalScore(0.8d)
                .qualityScore(0.7d)
                .engagementScore(0.6d)
                .conversionScore(0.5d)
                .explorationScore(0.4d)
                .freshnessScore(0.3d)
                .benefitScore(0.05d)
                .availabilityScore(0.04d)
                .finalScore(0.95d)
                .build());

        mockMvc.perform(get("/admin/places/recommendations/{requestId}/explanation", "admin-request-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("admin-request-1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.items[0].placeName").value("관리자 설명 장소"))
                .andExpect(jsonPath("$.items[0].userId").value(31))
                .andExpect(jsonPath("$.items[0].recommendationVersion").value("place-rec-v2"))
                .andExpect(jsonPath("$.items[0].recommendationStage").value("EXPERIMENTAL"))
                .andExpect(jsonPath("$.items[0].source").value("PERSONAL"))
                .andExpect(jsonPath("$.items[0].ranking").value(1))
                .andExpect(jsonPath("$.items[0].benefitScore").value(0.05d))
                .andExpect(jsonPath("$.items[0].availabilityScore").value(0.04d));
    }

    /** 추천 설명이 없는 요청 ID는 전용 오류 코드와 404로 응답하는지 확인. */
    @Test
    void rejectsMissingRecommendationExplanation() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places/recommendations/{requestId}/explanation", "missing-request-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EXPLANATION_NOT_FOUND"));
    }

    /** 숫자 키워드 7이 등록자 7에는 일치하고 77에는 부분 일치하지 않는지 확인. */
    @Test
    void matchesRegistrantIdExactly() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace firstPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("정확 일치 장소")
                .address("경상남도 진주시 테스트로 1")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(7L)
                .registrant("firstRegistrar")
                .build());

        mapPlaceRepository.save(MapPlace.builder()
                .name("부분 일치 장소")
                .address("경상남도 진주시 테스트로 2")
                .latitude(35.1895)
                .longitude(128.0790)
                .userId(77L)
                .registrant("secondRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(firstPlace.getId()))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    /** 숨겨진 장소도 관리자가 상세 조회할 수 있고 대표 이미지·관광 정보·작성자 및 연결 게시글 상태를 반환하는지 확인. */
    @Test
    void returnsPlaceWithLinkedPosts() throws Exception {
        String accessToken = createAdminAndLogin();
        User placeOwner = userRepository.save(User.builder()
                .username("placeOwner")
                .email("place-owner@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1997)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());

        MapPlace mapPlace = MapPlace.builder()
                .name("남강")
                .englishName("Nam River")
                .address("경상남도 진주시 남강변")
                .category("CULTURAL_HERITAGE")
                .touristSummary("진주의 대표 강변 산책 장소")
                .touristCategories(Set.of(TouristCategory.NIGHTLIFE))
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(placeOwner.getId())
                .registrant(placeOwner.getUsername())
                .build();
        mapPlace.updateImageUrl("https://example.com/places/namgang-representative.jpg");
        mapPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlace = mapPlaceRepository.save(mapPlace);

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/namgang.jpg")
                .s3Key("map/namgang.jpg")
                .title("남강 야경")
                .description("강변에서 촬영한 사진")
                .userId(15L)
                .username("placeOwner")
                .likeCount(7L)
                .mapPlace(mapPlace)
                .build());

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapPlace.getId()))
                .andExpect(jsonPath("$.name").value("남강"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/places/namgang-representative.jpg"))
                .andExpect(jsonPath("$.discoveryStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.category").value("CULTURAL_HERITAGE"))
                .andExpect(jsonPath("$.categoryName").value("문화재"))
                .andExpect(jsonPath("$.englishName").value("Nam River"))
                .andExpect(jsonPath("$.touristSummary").value("진주의 대표 강변 산책 장소"))
                .andExpect(jsonPath("$.touristCategories[0]").value("NIGHTLIFE"))
                .andExpect(jsonPath("$.username").value("placeOwner"))
                .andExpect(jsonPath("$.sortParam").value(SortParam.LATEST.name()))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.placeGrowth.level").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("남강 야경"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(7))
                .andExpect(jsonPath("$.posts[0].username").value("placeOwner"))
                .andExpect(jsonPath("$.posts[0].visibilityStatus").value("VISIBLE"))
                .andExpect(jsonPath("$.posts[0].hiddenReason").value(nullValue()));
    }

    /** 숨김 게시글의 사유·작성자·제목·생성 시각은 관리자에게 제공하고 공개 사진 수와 숨김 사진 수는 구분하는지 확인. */
    @Test
    void returnsHiddenPostDetails() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("숨김 게시글 장소")
                .address("경상남도 진주시 숨김로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(31L)
                .registrant("placeOwner")
                .build());

        MapImage hiddenPost = MapImage.builder()
                .imageUrl("https://example.com/hidden.jpg")
                .s3Key("map/hidden.jpg")
                .title("숨김 게시글 제목")
                .description("숨김 게시글 설명")
                .userId(32L)
                .username("bannedAuthor")
                .likeCount(3L)
                .mapPlace(mapPlace)
                .build();
        hiddenPost.autoHide("USER_BANNED", LocalDateTime.of(2026, 8, 12, 10, 0), 1L);
        mapImageRepository.save(hiddenPost);

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.placeGrowth.photoCount").value(0))
                .andExpect(jsonPath("$.placeGrowth.hiddenPhotoCount").value(1))
                .andExpect(jsonPath("$.posts[0].visibilityStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.posts[0].hiddenReason").value("USER_BANNED"))
                .andExpect(jsonPath("$.posts[0].title").value("숨김 게시글 제목"))
                .andExpect(jsonPath("$.posts[0].userId").value(32L))
                .andExpect(jsonPath("$.posts[0].username").value("bannedAuthor"))
                .andExpect(jsonPath("$.posts[0].createdAt").isNotEmpty());
    }

    /** 상세의 게시글 MOST_LIKED 정렬은 좋아요 9개 사진을 2개 사진보다 먼저 반환하는지 확인. */
    @Test
    void sortsPlacePostsByLikes() throws Exception {
        String accessToken = createAdminAndLogin();
        User placeOwner = userRepository.save(User.builder()
                .username("placeSortOwner")
                .email("place-sort-owner@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1996)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("촉석루")
                .address("경상남도 진주시 본성동")
                .latitude(35.1880)
                .longitude(128.0815)
                .userId(placeOwner.getId())
                .registrant(placeOwner.getUsername())
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/low-like.jpg")
                .s3Key("map/low-like.jpg")
                .title("좋아요 적은 사진")
                .description("첫 번째 사진")
                .userId(placeOwner.getId())
                .username("placeSortOwner")
                .likeCount(2L)
                .mapPlace(mapPlace)
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/high-like.jpg")
                .s3Key("map/high-like.jpg")
                .title("좋아요 많은 사진")
                .description("두 번째 사진")
                .userId(placeOwner.getId())
                .username("placeSortOwner")
                .likeCount(9L)
                .mapPlace(mapPlace)
                .build());

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortParam", SortParam.MOST_LIKED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortParam").value(SortParam.MOST_LIKED.name()))
                .andExpect(jsonPath("$.posts[0].title").value("좋아요 많은 사진"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(9))
                .andExpect(jsonPath("$.posts[1].title").value("좋아요 적은 사진"));
    }

    /** 존재하지 않는 장소 상세 조회가 PLACE_NOT_FOUND와 404를 반환하는지 확인. */
    @Test
    void rejectsMissingPlaceDetail() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    /** 장소 삭제 시 연결 게시글과 관광 정보 가드 행을 제거하고 장소·게시글 감사 기록 및 삭제 건수를 남기는지 확인. */
    @Test
    void deletePlaceDeletesLinkedPosts() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = MapPlace.builder()
                .name("삭제 대상 장소")
                .address("경상남도 진주시 삭제로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(94L)
                .registrant("deleteOwner")
                .photoCount(2L)
                .build();
        mapPlace.updateTouristInformation("Delete Target Place", null, Set.of());
        mapPlace = mapPlaceRepository.saveAndFlush(mapPlace);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_tourist_guard WHERE map_place_id = ?",
                Integer.class,
                mapPlace.getId()
        ));

        MapImage firstPost = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/delete-first.jpg")
                .s3Key("map/admin-place-delete-first-" + mapPlace.getId() + ".jpg")
                .title("삭제 대상 게시글 1")
                .description("장소와 함께 삭제될 게시글")
                .userId(201L)
                .username("postOwner1")
                .mapPlace(mapPlace)
                .build());
        MapImage secondPost = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/delete-second.jpg")
                .s3Key("map/admin-place-delete-second-" + mapPlace.getId() + ".jpg")
                .title("삭제 대상 게시글 2")
                .description("장소와 함께 삭제될 게시글")
                .userId(202L)
                .username("postOwner2")
                .mapPlace(mapPlace)
                .build());

        mockMvc.perform(delete("/admin/places/{id}/delete", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertFalse(mapPlaceRepository.existsById(mapPlace.getId()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_tourist_guard WHERE map_place_id = ?",
                Integer.class,
                mapPlace.getId()
        ));
        assertFalse(mapImageRepository.existsById(firstPost.getId()));
        assertFalse(mapImageRepository.existsById(secondPost.getId()));
        assertEquals(0L, mapImageRepository.countByMapPlace_Id(mapPlace.getId()));
        assertEquals(3L, adminAuditLogRepository.count());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.PLACE_DELETED));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.PLACE_DELETED)
                .anyMatch(log -> log.getAfterState().contains("\"deletedPostCount\":2")));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.PLACE_DELETED)
                .anyMatch(log -> log.getBeforeState().contains("\"operatingStatus\":\"OPERATING\"")));
    }

    /** 장소에 연결된 두 사용자의 북마크와 추세 이벤트를 제거하고 감사 기록의 삭제 북마크 수를 확인. */
    @Test
    void deletesPlaceBookmarkReferences() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("즐겨찾기 삭제 대상 장소")
                .address("경상남도 진주시 즐겨찾기로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(94L)
                .registrant("bookmarkDeleteOwner")
                .build());
        mapBookmarkRepository.saveAndFlush(MapBookmark.builder()
                .userId(201L)
                .placeId(mapPlace.getId())
                .build());
        mapBookmarkRepository.saveAndFlush(MapBookmark.builder()
                .userId(202L)
                .placeId(mapPlace.getId())
                .build());
        mapBookmarkTrendEventRepository.saveAndFlush(MapBookmarkTrendEvent.added(
                201L,
                mapPlace.getId(),
                LocalDateTime.of(2026, 9, 3, 10, 0)
        ));

        mockMvc.perform(delete("/admin/places/{id}/delete", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertFalse(mapPlaceRepository.existsById(mapPlace.getId()));
        assertEquals(0L, mapBookmarkRepository.countByPlaceId(mapPlace.getId()));
        assertTrue(mapBookmarkTrendEventRepository.findAll().stream()
                .noneMatch(event -> event.getPlaceId().equals(mapPlace.getId())));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.PLACE_DELETED)
                .anyMatch(log -> log.getAfterState().contains("\"deletedBookmarkCount\":2")));
    }

    /** 체크인이 연결된 장소 삭제는 409로 거부하고 장소와 체크인 이력을 보존하는지 확인. */
    @Test
    void blocksDeletionWithCheckIn() throws Exception {
        String accessToken = createAdminAndLogin();
        String touristUsername = "checkInTourist" + ADMIN_SEQUENCE.incrementAndGet();
        User tourist = userRepository.saveAndFlush(User.builder()
                .username(touristUsername)
                .email(touristUsername + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
        MapPlace mapPlace = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("체크인 이력 연결 장소")
                .address("경상남도 진주시 체크인로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(tourist.getId())
                .registrant(touristUsername)
                .build());
        Instant checkedInAt = Instant.parse("2026-07-20T01:00:00Z");
        locationCheckInRepository.saveAndFlush(LocationCheckIn.proximityMatched(
                tourist.getId(),
                mapPlace.getId(),
                LocalDate.of(2026, 7, 20),
                checkedInAt,
                checkedInAt,
                10.0
        ));

        mockMvc.perform(delete("/admin/places/{id}/delete", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_CHECK_IN_CONNECTED"));

        assertTrue(mapPlaceRepository.existsById(mapPlace.getId()));
        assertTrue(locationCheckInRepository.existsByPlaceId(mapPlace.getId()));
    }

    /** 좌표 API가 위경도·ADMIN 출처와 공간 좌표의 X=경도·Y=위도를 일관되게 저장하는지 확인. */
    @Test
    void updatesPlaceSpatialCoordinates() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("좌표 수정 장소")
                .address("경상남도 진주시 수정로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(90L)
                .registrant("coordinateOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/coordinates", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "latitude", 35.1796,
                                "longitude", 128.1076
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.latitude").value(35.1796))
                .andExpect(jsonPath("$.longitude").value(128.1076))
                .andExpect(jsonPath("$.message").value("장소 좌표를 수정했습니다."));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals(35.1796, updatedPlace.getLatitude());
        assertEquals(128.1076, updatedPlace.getLongitude());
        assertEquals(GeocodingSource.ADMIN, updatedPlace.getGeocodingSource());
        assertNotNull(updatedPlace.getLocation());
        assertEquals(128.1076, updatedPlace.getLocation().getX());
        assertEquals(35.1796, updatedPlace.getLocation().getY());
    }

    /** 이름·카테고리·사유를 정규화해 저장하고 감사 기록에 대상 ID 및 변경 전후 값을 보존하는지 확인. */
    @Test
    void auditsBasicInformationUpdate() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("기존 장소명")
                .category("CAFE")
                .address("경상남도 진주시 기본로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(90L)
                .registrant("basicInformationOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/basic-information", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "  진주성  ",
                                "category", "CULTURAL_HERITAGE",
                                "reason", "  장소명과 분류 오등록 정정  "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.name").value("진주성"))
                .andExpect(jsonPath("$.category").value("CULTURAL_HERITAGE"))
                .andExpect(jsonPath("$.modifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.message").value("장소 기본 정보를 수정했습니다."));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals("진주성", updatedPlace.getName());
        assertEquals(PlaceCategory.CULTURAL_HERITAGE.name(), updatedPlace.getCategory());

        assertEquals(1, adminAuditLogRepository.findAll().size());
        var auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.PLACE_BASIC_INFORMATION_UPDATED, auditLog.getAction());
        assertEquals(AdminAuditTargetType.PLACE, auditLog.getTargetType());
        assertEquals(String.valueOf(mapPlace.getId()), auditLog.getTargetId());
        assertEquals("장소명과 분류 오등록 정정", auditLog.getReason());
        assertTrue(auditLog.getBeforeState().contains("\"name\":\"기존 장소명\""));
        assertTrue(auditLog.getBeforeState().contains("\"category\":\"CAFE\""));
        assertTrue(auditLog.getAfterState().contains("\"name\":\"진주성\""));
        assertTrue(auditLog.getAfterState().contains("\"category\":\"CULTURAL_HERITAGE\""));
        assertNotNull(auditLog.getCreatedAt());
    }

    /** 기본 정보 변경의 공백 장소명을 400과 name 필드 검증 메시지로 거부하는지 확인. */
    @Test
    void rejectsBlankPlaceName() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("기본 정보 검증 장소")
                .category("CAFE")
                .address("경상남도 진주시 기본로 2")
                .latitude(35.1802)
                .longitude(128.1079)
                .build());

        mockMvc.perform(patch("/admin/places/{id}/basic-information", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "   ",
                                "category", "CAFE",
                                "reason", "장소명 검증"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("장소명은 필수입니다."));
    }

    /** 정의되지 않은 카테고리 변경은 400으로 거부하고 기존 카테고리와 빈 감사 기록을 유지하는지 확인. */
    @Test
    void rejectsInvalidPlaceCategory() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("카테고리 검증 장소")
                .category("CAFE")
                .address("경상남도 진주시 기본로 3")
                .latitude(35.1803)
                .longitude(128.1080)
                .build());

        mockMvc.perform(patch("/admin/places/{id}/basic-information", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "카테고리 검증 장소",
                                  "category": "INVALID",
                                  "reason": "유효하지 않은 카테고리 검증"
                                }
                                """))
                .andExpect(status().isBadRequest());

        MapPlace unchangedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals("CAFE", unchangedPlace.getCategory());
        assertTrue(adminAuditLogRepository.findAll().isEmpty());
    }

    /** 같은 장소 좌표를 두 번 연속 갱신해도 추천 재동기화 Outbox 행이 하나만 등록되는지 확인. 이벤트 상태별 필터는 직접 검증 대상에서 제외. */
    @Test
    void coalescesCoordinateResyncEvents() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("좌표 연속 수정 장소")
                .address("경상남도 진주시 수정로 2")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(90L)
                .registrant("coordinateOwner")
                .build());

        for (int index = 0; index < 2; index++) {
            mockMvc.perform(patch("/admin/places/{id}/coordinates", mapPlace.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "latitude", 35.1796 + (index * 0.0001),
                                    "longitude", 128.1076 + (index * 0.0001)
                            ))))
                    .andExpect(status().isOk());
        }

        long waitingResyncEventCount = outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED)
                .filter(event -> event.getAggregateId().equals(String.valueOf(mapPlace.getId())))
                .count();
        assertEquals(1L, waitingResyncEventCount);
    }

    /** 주소 공백을 정리하고 관리자 출처로 저장하며 주소 검수 감사 기록과 추천 재동기화 Outbox 등록을 확인. */
    @Test
    void auditsNormalizedGeocodingUpdate() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("주소 보정 장소")
                .address("기존 주소")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(90L)
                .registrant("geocodingOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/geocoding", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "address", "경상남도 진주시 남강로 626",
                                "roadAddress", " 경상남도 진주시 남강로 626 ",
                                "jibunAddress", "경상남도 진주시 본성동 500-8",
                                "postalCode", "52692",
                                "latitude", 35.1894,
                                "longitude", 128.0789,
                                "reason", "관리자 주소 검수"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roadAddress").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.jibunAddress").value("경상남도 진주시 본성동 500-8"))
                .andExpect(jsonPath("$.postalCode").value("52692"))
                .andExpect(jsonPath("$.geocodingSource").value("ADMIN"));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals(GeocodingSource.ADMIN, updatedPlace.getGeocodingSource());
        assertEquals("경상남도 진주시 남강로 626", updatedPlace.getRoadAddress());
        assertEquals(1, adminAuditLogRepository.findAll().size());
        assertEquals(
                AdminAuditAction.PLACE_GEOCODING_UPDATED,
                adminAuditLogRepository.findAll().getFirst().getAction()
        );
        assertEquals("관리자 주소 검수", adminAuditLogRepository.findAll().getFirst().getReason());
        assertTrue(outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getEventType() == OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED));
    }

    /** Kakao 장소 ID를 교체하면 응답·저장 값과 ID 변경 감사 기록이 반영되는지 확인. */
    @Test
    void reconnectsKakaoPlaceIdentifier() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("카카오 재연결 장소")
                .address("경상남도 진주시 연결로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .kakaoPlaceId("old-place-id")
                .userId(91L)
                .registrant("kakaoOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/kakao-place-id", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kakaoPlaceId", "27414316"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.kakaoPlaceId").value("27414316"))
                .andExpect(jsonPath("$.message").value("장소 Kakao place id를 수정했습니다."));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals("27414316", updatedPlace.getKakaoPlaceId());
        assertEquals(1, adminAuditLogRepository.findAll().size());
        assertEquals(AdminAuditAction.PLACE_KAKAO_PLACE_ID_UPDATED, adminAuditLogRepository.findAll().getFirst().getAction());
    }

    /** 다른 장소가 사용하는 Kakao ID로 변경하면 전용 충돌 코드와 409를 반환하는지 확인. */
    @Test
    void rejectsDuplicateKakaoIdentifier() throws Exception {
        String accessToken = createAdminAndLogin();

        mapPlaceRepository.save(MapPlace.builder()
                .name("기존 연결 장소")
                .address("경상남도 진주시 연결로 2")
                .latitude(35.1802)
                .longitude(128.1079)
                .kakaoPlaceId("27414316")
                .userId(92L)
                .registrant("existingOwner")
                .build());
        MapPlace targetPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("변경 대상 장소")
                .address("경상남도 진주시 연결로 3")
                .latitude(35.1803)
                .longitude(128.1080)
                .userId(93L)
                .registrant("targetOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/kakao-place-id", targetPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kakaoPlaceId", "27414316"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_KAKAO_PLACE_ID_CONFLICT"));
    }

    /** 영문명·설명·사유의 공백을 정리하고 카테고리를 갱신하며 관광 정보의 변경 전후를 감사 로그에 남기는지 확인. */
    @Test
    void auditsTouristInformationUpdate() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("관광 정보 수정 장소")
                .englishName("Old tourist name")
                .address("경상남도 진주시 관광로 1")
                .touristSummary("기존 관광 요약")
                .touristCategories(Set.of(TouristCategory.OTHER))
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(95L)
                .registrant("touristInfoOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/tourist-info", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "englishName", "  Jinju Tourist Spot  ",
                                "touristSummary", "  관광객이 방문하기 좋은 장소  ",
                                "touristCategories", List.of("K_POP", "CAFE"),
                                "reason", "  관광 정보 최신화  "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.englishName").value("Jinju Tourist Spot"))
                .andExpect(jsonPath("$.touristSummary").value("관광객이 방문하기 좋은 장소"))
                .andExpect(jsonPath("$.touristCategories", containsInAnyOrder("K_POP", "CAFE")))
                .andExpect(jsonPath("$.message").value("장소 관광 정보를 수정했습니다."));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals("Jinju Tourist Spot", updatedPlace.getEnglishName());
        assertEquals("관광객이 방문하기 좋은 장소", updatedPlace.getTouristSummary());

        assertEquals(1, adminAuditLogRepository.findAll().size());
        var auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.PLACE_TOURIST_INFO_UPDATED, auditLog.getAction());
        assertEquals(AdminAuditTargetType.PLACE, auditLog.getTargetType());
        assertEquals(String.valueOf(mapPlace.getId()), auditLog.getTargetId());
        assertEquals("관광 정보 최신화", auditLog.getReason());
        assertTrue(auditLog.getBeforeState().contains("\"englishName\":\"Old tourist name\""));
        assertTrue(auditLog.getBeforeState().contains("\"touristCategories\":[\"OTHER\"]"));
        assertTrue(auditLog.getAfterState().contains("\"englishName\":\"Jinju Tourist Spot\""));
        assertTrue(auditLog.getAfterState().contains("\"touristCategories\":[\"K_POP\",\"CAFE\"]"));
    }

    /** 임시 휴업 상태·확인 시각을 저장하고 감사 로그에 변경 전후 상태와 확인 사유를 기록하는지 확인. */
    @Test
    void auditsOperatingStatusUpdate() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("운영 상태 확인 장소")
                .address("경상남도 진주시 운영로 10")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(95L)
                .registrant("operatingStatusOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/operating-status", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatingStatus", "TEMPORARILY_CLOSED",
                                "reason", "현장 확인 결과 임시 휴업"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.operatingStatus").value("TEMPORARILY_CLOSED"))
                .andExpect(jsonPath("$.operatingStatusCheckedAt").isNotEmpty())
                .andExpect(jsonPath("$.message").value("장소 운영 상태를 수정했습니다."));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals(PlaceOperatingStatus.TEMPORARILY_CLOSED, updatedPlace.getOperatingStatus());
        assertNotNull(updatedPlace.getOperatingStatusCheckedAt());

        var auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.PLACE_OPERATING_STATUS_UPDATED, auditLog.getAction());
        assertEquals("현장 확인 결과 임시 휴업", auditLog.getReason());
        assertTrue(auditLog.getBeforeState().contains("\"operatingStatus\":\"OPERATING\""));
        assertTrue(auditLog.getAfterState().contains("\"operatingStatus\":\"TEMPORARILY_CLOSED\""));
    }

    /** 관리자의 노출 숨김 요청이 저장 상태·감사 로그·전이 카운터 증가에 반영되는지 확인. 일반 사용자 거부는 별도 테스트가 담당. */
    @Test
    void auditsDiscoveryStatusUpdate() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("탐색 노출 검수 장소")
                .address("경상남도 진주시 탐색로 10")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(96L)
                .registrant("discoveryStatusOwner")
                .build());
        double beforeMetricCount = discoveryStatusUpdateCount(
                PlaceDiscoveryStatus.VISIBLE,
                PlaceDiscoveryStatus.HIDDEN
        );

        mockMvc.perform(patch("/admin/places/{id}/discovery-status", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "discoveryStatus", "HIDDEN",
                                "reason", "중복 장소 검수 전 임시 숨김"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.discoveryStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.message").value("장소 탐색 노출 상태를 수정했습니다."));

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals(PlaceDiscoveryStatus.HIDDEN, updatedPlace.getDiscoveryStatus());

        var auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.PLACE_DISCOVERY_STATUS_UPDATED, auditLog.getAction());
        assertEquals(AdminAuditTargetType.PLACE, auditLog.getTargetType());
        assertEquals(String.valueOf(mapPlace.getId()), auditLog.getTargetId());
        assertEquals("중복 장소 검수 전 임시 숨김", auditLog.getReason());
        assertTrue(auditLog.getBeforeState().contains("\"discoveryStatus\":\"VISIBLE\""));
        assertTrue(auditLog.getAfterState().contains("\"discoveryStatus\":\"HIDDEN\""));
        assertEquals(
                beforeMetricCount + 1.0d,
                discoveryStatusUpdateCount(PlaceDiscoveryStatus.VISIBLE, PlaceDiscoveryStatus.HIDDEN),
                0.0001d
        );
    }

    /** 일반 사용자의 관리자 노출 상태 변경을 403으로 막는지 확인. */
    @Test
    void rejectsUserDiscoveryUpdate() throws Exception {
        String accessToken = createUserAndLogin("normalDiscoveryUser", UserRole.USER);
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("일반 사용자 접근 차단 장소")
                .address("경상남도 진주시 권한로 10")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(96L)
                .registrant("discoveryStatusOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/discovery-status", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "discoveryStatus", "HIDDEN",
                                "reason", "권한 검증"
                        ))))
                .andExpect(status().isForbidden());
    }

    /** 근거 생성·목록·관리자 검토를 HTTP로 연결해 장소 검증 요약과 시각, 제출·검토 메트릭, Outbox와 감사 기록을 확인. */
    @Test
    void reviewsEvidenceWithTrackedEffects() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("정보 출처 검증 장소")
                .address("경상남도 진주시 증빙로 1")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(97L)
                .registrant("informationEvidenceOwner")
                .build());
        double beforeSubmitMetricCount = informationEvidenceSubmittedCount(PlaceInformationSourceType.ADMIN);
        double beforeReviewMetricCount = informationVerificationStatusUpdateCount(
                PlaceInformationVerificationStatus.UNVERIFIED,
                PlaceInformationVerificationStatus.ADMIN_VERIFIED
        );

        MvcResult createResult = mockMvc.perform(post("/admin/places/{id}/information-evidence", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceType", "ADMIN",
                                "evidenceType", "DOCUMENT",
                                "referenceUrl", "https://example.com/evidence/place-97",
                                "description", "관리자 현장 확인 자료"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.evidence.sourceType").value("ADMIN"))
                .andExpect(jsonPath("$.evidence.evidenceType").value("DOCUMENT"))
                .andExpect(jsonPath("$.evidence.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.message").value("장소 정보 증빙을 등록했습니다."))
                .andReturn();

        long evidenceId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("evidence")
                .path("evidenceId")
                .asLong();
        MapPlace submittedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals(PlaceInformationSourceType.ADMIN, submittedPlace.getPrimaryInformationSource());
        assertEquals(PlaceInformationVerificationStatus.UNVERIFIED, submittedPlace.getInformationVerificationStatus());
        assertNotNull(submittedPlace.getInformationEvidenceUpdatedAt());
        assertEquals(
                beforeSubmitMetricCount + 1.0d,
                informationEvidenceSubmittedCount(PlaceInformationSourceType.ADMIN),
                0.0001d
        );
        assertTrue(outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getEventType() == OutboxEventType.PLACE_INFORMATION_EVIDENCE_SUBMITTED));

        mockMvc.perform(get("/admin/places/{id}/information-evidence", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.evidences", hasSize(1)))
                .andExpect(jsonPath("$.evidences[0].evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.evidences[0].verificationStatus").value("UNVERIFIED"));

        mockMvc.perform(patch("/admin/places/{id}/information-evidence/{evidenceId}/review", mapPlace.getId(), evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verificationStatus", "ADMIN_VERIFIED",
                                "reviewReason", "관리자 검수 완료"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.evidence.verificationStatus").value("ADMIN_VERIFIED"))
                .andExpect(jsonPath("$.evidence.reviewReason").value("관리자 검수 완료"))
                .andExpect(jsonPath("$.message").value("장소 정보 증빙 검토 상태를 수정했습니다."));

        MapPlace reviewedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertEquals(PlaceInformationVerificationStatus.ADMIN_VERIFIED, reviewedPlace.getInformationVerificationStatus());
        assertNotNull(reviewedPlace.getInformationVerifiedAt());
        assertNotNull(reviewedPlace.getInformationEvidenceUpdatedAt());
        assertEquals(
                beforeReviewMetricCount + 1.0d,
                informationVerificationStatusUpdateCount(
                        PlaceInformationVerificationStatus.UNVERIFIED,
                        PlaceInformationVerificationStatus.ADMIN_VERIFIED
                ),
                0.0001d
        );
        assertTrue(outboxEventRepository.findAll().stream()
                .anyMatch(event -> event.getEventType() == OutboxEventType.PLACE_INFORMATION_VERIFICATION_UPDATED));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.PLACE_INFORMATION_EVIDENCE_UPDATED
                        && log.getTargetType() == AdminAuditTargetType.PLACE_INFORMATION_EVIDENCE
                        && log.getTargetId().equals(String.valueOf(evidenceId))));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.PLACE_INFORMATION_VERIFICATION_UPDATED
                        && log.getReason().equals("관리자 검수 완료")));
    }

    /** 출처와 유형만 있고 참조·설명 payload가 없는 근거 등록 요청을 400으로 거부하는지 확인. */
    @Test
    void rejectsEmptyPlaceEvidence() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("빈 증빙 거부 장소")
                .address("경상남도 진주시 증빙로 2")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(98L)
                .registrant("emptyEvidenceOwner")
                .build());

        mockMvc.perform(post("/admin/places/{id}/information-evidence", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceType", "ADMIN",
                                "evidenceType", "DOCUMENT"
                        ))))
                .andExpect(status().isBadRequest());
    }

    /** 관리자 검토에서 허용하지 않는 SOURCE_CONFIRMED는 400, 존재하지 않는 근거 ID는 404로 구분하는지 확인. */
    @Test
    void rejectsInvalidEvidenceReview() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("검토 실패 장소")
                .address("경상남도 진주시 증빙로 3")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(99L)
                .registrant("invalidReviewOwner")
                .build());

        MvcResult createResult = mockMvc.perform(post("/admin/places/{id}/information-evidence", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceType", "USER_REPORT",
                                "evidenceType", "PHOTO",
                                "referenceUrl", "https://example.com/evidence/photo"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        long evidenceId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("evidence")
                .path("evidenceId")
                .asLong();

        mockMvc.perform(patch("/admin/places/{id}/information-evidence/{evidenceId}/review", mapPlace.getId(), evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verificationStatus", "SOURCE_CONFIRMED",
                                "reviewReason", "관리자 검토 API에서는 허용하지 않는 상태"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/admin/places/{id}/information-evidence/{evidenceId}/review", mapPlace.getId(), 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verificationStatus", "ADMIN_VERIFIED",
                                "reviewReason", "없는 증빙"
                        ))))
                .andExpect(status().isNotFound());
    }

    /** 일반 사용자의 근거 생성은 403으로 막고 관리자의 없는 장소 근거 조회는 404로 응답하는지 확인. */
    @Test
    void guardsPlaceEvidenceAccess() throws Exception {
        String userToken = createUserAndLogin("normalInformationEvidenceUser", UserRole.USER);
        String adminToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("증빙 권한 장소")
                .address("경상남도 진주시 증빙로 4")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(100L)
                .registrant("evidencePermissionOwner")
                .build());

        mockMvc.perform(post("/admin/places/{id}/information-evidence", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sourceType", "ADMIN",
                                "evidenceType", "ADMIN_REVIEW",
                                "description", "권한 테스트"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/places/{id}/information-evidence", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    /** 일반·야간 영업시간과 휴무·변경시간 예외를 저장해 DB 행 개수, 상세 조회, 감사 로그에 일정이 반영되는지 확인. */
    @Test
    void persistsAuditedOperatingSchedule() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("영업시간 수정 장소")
                .address("경상남도 진주시 영업로 1")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(95L)
                .registrant("operatingScheduleOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/operating-schedule", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "regularHours", List.of(
                                        Map.of("dayOfWeek", "MONDAY", "opensAt", "09:00", "closesAt", "18:00"),
                                        Map.of("dayOfWeek", "FRIDAY", "opensAt", "20:00", "closesAt", "02:00")
                                ),
                                "exceptions", List.of(
                                        Map.of("date", "2026-08-15", "closed", true, "hours", List.of()),
                                        Map.of(
                                                "date", "2026-08-16",
                                                "closed", false,
                                                "hours", List.of(Map.of("opensAt", "10:00", "closesAt", "16:00"))
                                        )
                                ),
                                "reason", "광복절 휴무와 주말 영업시간 반영"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.regularHours", hasSize(2)))
                .andExpect(jsonPath("$.regularHours[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.exceptions", hasSize(2)))
                .andExpect(jsonPath("$.exceptions[0].date").value("2026-08-15"))
                .andExpect(jsonPath("$.exceptions[0].closed").value(true))
                .andExpect(jsonPath("$.exceptions[1].hours[0].opensAt").value("10:00:00"))
                .andExpect(jsonPath("$.message").value("장소 영업시간 일정을 수정했습니다."));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_regular_operating_hour WHERE map_place_id = ?",
                Integer.class,
                mapPlace.getId()
        ));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_operating_exception WHERE map_place_id = ?",
                Integer.class,
                mapPlace.getId()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_operating_exception_hour",
                Integer.class
        ));

        var auditLog = adminAuditLogRepository.findAll().getFirst();
        assertEquals(AdminAuditAction.PLACE_OPERATING_SCHEDULE_UPDATED, auditLog.getAction());
        assertEquals("광복절 휴무와 주말 영업시간 반영", auditLog.getReason());
        assertTrue(auditLog.getAfterState().contains("\"dayOfWeek\":\"MONDAY\""));
        assertTrue(auditLog.getAfterState().contains("\"date\":\"2026-08-15\""));

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularHours", hasSize(2)))
                .andExpect(jsonPath("$.operatingExceptions", hasSize(2)));
    }

    /** 같은 월요일의 09~18시와 17~21시 구간 중복을 일정 입력 오류로 거부하는지 확인. */
    @Test
    void rejectsOverlappingRegularHours() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("영업시간 검증 장소")
                .address("경상남도 진주시 영업로 2")
                .latitude(35.1805)
                .longitude(128.1082)
                .userId(96L)
                .registrant("operatingScheduleOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/operating-schedule", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "regularHours", List.of(
                                        Map.of("dayOfWeek", "MONDAY", "opensAt", "09:00", "closesAt", "18:00"),
                                        Map.of("dayOfWeek", "MONDAY", "opensAt", "17:00", "closesAt", "21:00")
                                ),
                                "reason", "중복 시간대 검증"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_OPERATING_SCHEDULE_INVALID_REQUEST"));
    }

    /** 전날 22~02시 예외와 다음날 01~03시 예외가 날짜를 넘어 겹치면 거부하는지 확인. */
    @Test
    void rejectsOvernightExceptionOverlap() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("익일 예외 일정 검증 장소")
                .address("경상남도 진주시 영업로 3")
                .latitude(35.1806)
                .longitude(128.1083)
                .userId(97L)
                .registrant("operatingScheduleOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/operating-schedule", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "exceptions", List.of(
                                        Map.of(
                                                "date", "2026-08-01",
                                                "closed", false,
                                                "hours", List.of(Map.of("opensAt", "22:00", "closesAt", "02:00"))
                                        ),
                                        Map.of(
                                                "date", "2026-08-02",
                                                "closed", false,
                                                "hours", List.of(Map.of("opensAt", "01:00", "closesAt", "03:00"))
                                        )
                                ),
                                "reason", "익일 예외 일정 중복 검증"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_OPERATING_SCHEDULE_INVALID_REQUEST"));
    }

    /** 전날부터 다음날 02시까지 이어지는 예외와 다음날 전일 휴무의 충돌을 거부하는지 확인. */
    @Test
    void rejectsClosureAfterOvernightException() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("휴무 예외 일정 검증 장소")
                .address("경상남도 진주시 영업로 4")
                .latitude(35.1807)
                .longitude(128.1084)
                .userId(98L)
                .registrant("operatingScheduleOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/operating-schedule", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "exceptions", List.of(
                                        Map.of(
                                                "date", "2026-08-01",
                                                "closed", false,
                                                "hours", List.of(Map.of("opensAt", "22:00", "closesAt", "02:00"))
                                        ),
                                        Map.of("date", "2026-08-02", "closed", true, "hours", List.of())
                                ),
                                "reason", "익일 휴무 예외 일정 중복 검증"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_OPERATING_SCHEDULE_INVALID_REQUEST"));
    }

    /** 사유만 전달해 선택 관광 정보를 생략하면 영문명·설명·카테고리와 관광 정보 가드 행이 제거되는지 확인. */
    @Test
    void clearsOmittedTouristInformation() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = MapPlace.builder()
                .name("관광 정보 초기화 장소")
                .address("경상남도 진주시 관광로 2")
                .latitude(35.1805)
                .longitude(128.1082)
                .userId(96L)
                .registrant("touristInfoOwner")
                .build();
        mapPlace.updateTouristInformation(
                "Tourist Place",
                "초기화할 관광 요약",
                Set.of(TouristCategory.FOOD)
        );
        mapPlace = mapPlaceRepository.saveAndFlush(mapPlace);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_tourist_guard WHERE map_place_id = ?",
                Integer.class,
                mapPlace.getId()
        ));

        mockMvc.perform(patch("/admin/places/{id}/tourist-info", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", "잘못 등록된 관광 정보 초기화"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.englishName").value(nullValue()))
                .andExpect(jsonPath("$.touristSummary").value(nullValue()))
                .andExpect(jsonPath("$.touristCategories").isEmpty());

        MapPlace updatedPlace = mapPlaceRepository.findById(mapPlace.getId()).orElseThrow();
        assertNull(updatedPlace.getEnglishName());
        assertNull(updatedPlace.getTouristSummary());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_tourist_guard WHERE map_place_id = ?",
                Integer.class,
                mapPlace.getId()
        ));

        mockMvc.perform(get("/admin/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.touristCategories").isEmpty());
    }

    /** 관광 정보 변경에 공백 사유를 보내면 reason 필드 오류로 400 응답하는지 확인. */
    @Test
    void rejectsBlankTouristUpdateReason() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("관광 정보 검증 장소")
                .address("경상남도 진주시 관광로 3")
                .latitude(35.1806)
                .longitude(128.1083)
                .userId(97L)
                .registrant("touristInfoOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/tourist-info", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "englishName", "Jinju Place",
                                "reason", "   "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("수정 사유는 필수입니다."));
    }

    /** 관광 카테고리 배열 내부의 null 원소를 400으로 거부하는지 확인. */
    @Test
    void rejectsNullTouristCategoryElement() throws Exception {
        String accessToken = createAdminAndLogin();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("관광 카테고리 검증 장소")
                .address("경상남도 진주시 관광로 4")
                .latitude(35.1807)
                .longitude(128.1084)
                .userId(98L)
                .registrant("touristInfoOwner")
                .build());

        mockMvc.perform(patch("/admin/places/{id}/tourist-info", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "touristCategories", java.util.Collections.singletonList(null),
                                "reason", "카테고리 검증"
                        ))))
                .andExpect(status().isBadRequest());
    }

    /** 두 버전의 70/30 비율과 비활성 fallback 정책을 저장하고 정책 수와 kill switch 감사 기록을 확인. */
    @Test
    void updatesRecommendationTrafficPolicy() throws Exception {
        String accessToken = createAdminAndLogin();

        MvcResult result = mockMvc.perform(patch("/admin/places/recommendation-traffic")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "reason", "추천 트래픽 비율 조정",
                                "policies", List.of(
                                        java.util.Map.of(
                                                "recommendationVersion", "place-rec-v1",
                                                "trafficPercentage", 70,
                                                "enabled", true
                                        ),
                                        java.util.Map.of(
                                                "recommendationVersion", "place-rec-v2",
                                                "trafficPercentage", 30,
                                                "enabled", false,
                                                "fallbackVersion", "place-rec-v1"
                                        )
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultVersion").value("place-rec-v1"))
                .andExpect(jsonPath("$.policies.length()").value(2))
                .andExpect(jsonPath("$.message").value("추천 버전 트래픽 비율을 수정했습니다."))
                .andExpect(jsonPath("$.policies[0].enabled").isBoolean())
                .andReturn();

        List<?> policies = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("policies")
                .traverse(objectMapper)
                .readValueAs(List.class);
        Map<?, ?> firstPolicy = assertInstanceOf(Map.class, policies.get(0));
        Map<?, ?> secondPolicy = assertInstanceOf(Map.class, policies.get(1));
        Map<String, Integer> trafficByVersion = Map.of(
                String.valueOf(firstPolicy.get("recommendationVersion")),
                ((Number) firstPolicy.get("trafficPercentage")).intValue(),
                String.valueOf(secondPolicy.get("recommendationVersion")),
                ((Number) secondPolicy.get("trafficPercentage")).intValue()
        );
        assertEquals(70, trafficByVersion.get("place-rec-v1"));
        assertEquals(30, trafficByVersion.get("place-rec-v2"));

        assertEquals(2L, placeRecommendationTrafficPolicyRepository.count());
        assertEquals(
                AdminAuditAction.PLACE_RECOMMENDATION_KILL_SWITCH_UPDATED,
                adminAuditLogRepository.findAll().getFirst().getAction()
        );
    }

    /** 활성 버전 비율 합계가 80이면 합계 오류로 400을 반환하는지 확인. */
    @Test
    void rejectsIncompleteTrafficTotal() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(patch("/admin/places/recommendation-traffic")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "reason", "추천 트래픽 비율 검증",
                                "policies", List.of(
                                        java.util.Map.of("recommendationVersion", "place-rec-v1", "trafficPercentage", 60, "enabled", true),
                                        java.util.Map.of("recommendationVersion", "place-rec-v2", "trafficPercentage", 20, "enabled", true)
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID"));
    }

    /** 전체 버전 중 하나만 전달한 정책 갱신을 합계 오류로 거부하는지 확인. */
    @Test
    void rejectsPartialTrafficPolicies() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(patch("/admin/places/recommendation-traffic")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "reason", "추천 트래픽 비율 검증",
                                "policies", List.of(
                                        java.util.Map.of("recommendationVersion", "place-rec-v1", "trafficPercentage", 100, "enabled", true)
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_TRAFFIC_POLICY_TOTAL_INVALID"));
    }

    /** 비활성 버전에서 fallback을 생략하면 정책 입력 오류로 거부하는지 확인. */
    @Test
    void requiresDisabledVersionFallback() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(patch("/admin/places/recommendation-traffic")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "reason", "추천 트래픽 비율 검증",
                                "policies", List.of(
                                        java.util.Map.of("recommendationVersion", "place-rec-v1", "trafficPercentage", 100, "enabled", true),
                                        java.util.Map.of("recommendationVersion", "place-rec-v2", "trafficPercentage", 0, "enabled", false)
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST"));
    }

    /** 비활성 두 버전이 서로를 fallback으로 지정한 순환을 입력 오류로 거부하는지 확인. */
    @Test
    void rejectsTrafficFallbackCycle() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(patch("/admin/places/recommendation-traffic")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "reason", "추천 트래픽 비율 검증",
                                "policies", List.of(
                                        java.util.Map.of(
                                                "recommendationVersion", "place-rec-v1",
                                                "trafficPercentage", 50,
                                                "enabled", false,
                                                "fallbackVersion", "place-rec-v2"
                                        ),
                                        java.util.Map.of(
                                                "recommendationVersion", "place-rec-v2",
                                                "trafficPercentage", 50,
                                                "enabled", false,
                                                "fallbackVersion", "place-rec-v1"
                                        )
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_TRAFFIC_POLICY_INVALID_REQUEST"));
    }

    /** 이름·주소와 가까운 좌표가 같은 두 장소만 중복 그룹으로 묶고 대표 ID와 판정 이유를 반환하는지 확인. */
    @Test
    void listDuplicatePlacesReturnsDuplicateGroups() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace firstPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("중복 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .latitude(35.642738)
                .longitude(128.391626)
                .userId(10L)
                .registrant("ownerA")
                .photoCount(1L)
                .build());
        MapPlace secondPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("중복 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .latitude(35.642900)
                .longitude(128.391700)
                .userId(11L)
                .registrant("ownerB")
                .photoCount(2L)
                .build());
        mapPlaceRepository.save(MapPlace.builder()
                .name("다른 장소")
                .address("서울특별시 강남구 테헤란로 1")
                .latitude(37.4981)
                .longitude(127.0276)
                .userId(12L)
                .registrant("ownerC")
                .photoCount(1L)
                .build());

        mockMvc.perform(get("/admin/places/duplicates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.groups[0].representativePlaceId").value(firstPlace.getId()))
                .andExpect(jsonPath("$.groups[0].duplicatePlaceIds.length()").value(2))
                .andExpect(jsonPath("$.groups[0].duplicatePlaceIds[1]").value(secondPlace.getId()))
                .andExpect(jsonPath("$.groups[0].reasons[0]").value("NAME_ADDRESS_COORDINATE"));
    }

    /** 지정 장소의 상세 중복 후보로 가까운 동명·동주소 장소 한 건과 판정 이유를 반환하는지 확인. */
    @Test
    void getDuplicatePlaceReturnsCandidates() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace firstPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("후보 장소")
                .address("부산광역시 수영구 광안해변로 219")
                .latitude(35.153169)
                .longitude(129.118666)
                .userId(20L)
                .registrant("ownerA")
                .photoCount(3L)
                .build());
        MapPlace secondPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("후보 장소")
                .address("부산광역시 수영구 광안해변로 219")
                .latitude(35.153200)
                .longitude(129.118690)
                .userId(21L)
                .registrant("ownerB")
                .photoCount(4L)
                .build());

        mockMvc.perform(get("/admin/places/duplicates/{id}", firstPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstPlace.getId()))
                .andExpect(jsonPath("$.candidates.length()").value(1))
                .andExpect(jsonPath("$.candidates[0].id").value(secondPlace.getId()))
                .andExpect(jsonPath("$.candidates[0].reason").value("NAME_ADDRESS_COORDINATE"));
    }

    /** 장소 병합이 원본 삭제·Kakao ID 이전·게시글 이동·북마크/전환 중복 정리·추천 snapshot 재집계와 감사/복구 이력을 함께 남기는지 확인. */
    @Test
    void mergesPlaceReferencesAndMetrics() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace sourcePlace = mapPlaceRepository.save(MapPlace.builder()
                .name("병합 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .kakaoPlaceId("27414316")
                .latitude(35.642738)
                .longitude(128.391626)
                .userId(30L)
                .registrant("sourceOwner")
                .photoCount(1L)
                .build());
        MapPlace targetPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("병합 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .latitude(35.642900)
                .longitude(128.391700)
                .userId(31L)
                .registrant("targetOwner")
                .photoCount(1L)
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/source.jpg")
                .s3Key("map/source.jpg")
                .title("source")
                .description("source image")
                .userId(100L)
                .username("sourceUser")
                .likeCount(4L)
                .mapPlace(sourcePlace)
                .build());
        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/target.jpg")
                .s3Key("map/target.jpg")
                .title("target")
                .description("target image")
                .userId(101L)
                .username("targetUser")
                .likeCount(3L)
                .mapPlace(targetPlace)
                .build());
        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/hidden.jpg")
                .s3Key("map/hidden.jpg")
                .title("hidden")
                .description("hidden image")
                .userId(102L)
                .username("hiddenUser")
                .likeCount(0L)
                .visibilityStatus(MapImageVisibilityStatus.AUTO_HIDDEN)
                .mapPlace(sourcePlace)
                .build());

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(200L)
                .placeId(sourcePlace.getId())
                .build());
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(201L)
                .placeId(sourcePlace.getId())
                .build());
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(201L)
                .placeId(targetPlace.getId())
                .build());

        placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(sourcePlace.getId())
                .userId(300L)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                .placeId(sourcePlace.getId())
                .userId(301L)
                .requestLatitude(35.642738)
                .requestLongitude(128.391626)
                .ranking(1)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(1L)
                .placeId(sourcePlace.getId())
                .userId(400L)
                .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(2L)
                .placeId(sourcePlace.getId())
                .userId(401L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(3L)
                .placeId(targetPlace.getId())
                .userId(401L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v1")
                .build());

        mockMvc.perform(post("/admin/places/merge")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "sourcePlaceId", sourcePlace.getId(),
                                        "targetPlaceId", targetPlace.getId()
                                )
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourcePlaceId").value(sourcePlace.getId()))
                .andExpect(jsonPath("$.targetPlaceId").value(targetPlace.getId()))
                .andExpect(jsonPath("$.message").value("중복 장소를 병합했습니다."));

        assertFalse(mapPlaceRepository.existsById(sourcePlace.getId()));
        assertTrue(mapPlaceRepository.existsById(targetPlace.getId()));
        assertEquals("27414316", mapPlaceRepository.findById(targetPlace.getId()).orElseThrow().getKakaoPlaceId());
        assertEquals(3L, mapImageRepository.countByMapPlace_Id(targetPlace.getId()));
        assertEquals(2L, mapPlaceRepository.findById(targetPlace.getId()).orElseThrow().currentPhotoCount());
        assertEquals(2L, mapBookmarkRepository.countByPlaceId(targetPlace.getId()));

        List<PlaceRecommendationConversionRepository.PlaceConversionCountProjection> conversionCounts =
                placeRecommendationConversionRepository.countConversionsByPlaceIds(List.of(targetPlace.getId()));
        long totalConversionCount = conversionCounts.stream()
                .mapToLong(PlaceRecommendationConversionRepository.PlaceConversionCountProjection::getConversionCount)
                .sum();
        assertEquals(2L, totalConversionCount);
        assertEquals(1L, placeRecommendationClickRepository.countByPlaceId(targetPlace.getId()));

        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(targetPlace.getId())
                .orElseThrow();
        assertEquals(2L, snapshot.getPhotoCount());
        assertEquals(2L, snapshot.getBookmarkCount());
        assertEquals(7L, snapshot.getTotalLikeCount());
        assertEquals(1L, snapshot.getClickCount());
        assertEquals(1L, snapshot.getBookmarkConversionCount());
        assertEquals(1L, snapshot.getLikeConversionCount());
        assertEquals(1L, snapshot.getExposureCount());
        assertEquals(1, adminAuditLogRepository.findAll().size());
        assertEquals(AdminAuditAction.PLACE_MERGED, adminAuditLogRepository.findAll().getFirst().getAction());
        assertEquals(AdminAuditTargetType.PLACE, adminAuditLogRepository.findAll().getFirst().getTargetType());
        assertEquals(String.valueOf(targetPlace.getId()), adminAuditLogRepository.findAll().getFirst().getTargetId());
        assertTrue(adminAuditLogRepository.findAll().getFirst().getAfterState()
                .contains("\"sourcePlaceDeleted\":true"));
        assertEquals(1, adminPlaceMergeHistoryRepository.findAll().size());
    }

    /** 원본에 체크인이 있으면 병합을 409로 막고 두 장소와 원본 체크인을 보존하는지 확인. */
    @Test
    void blocksMergeWithSourceCheckIn() throws Exception {
        String accessToken = createAdminAndLogin();
        String touristUsername = "mergeCheckInTourist" + ADMIN_SEQUENCE.incrementAndGet();
        User tourist = userRepository.saveAndFlush(User.builder()
                .username(touristUsername)
                .email(touristUsername + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
        MapPlace sourcePlace = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("체크인 병합 장소")
                .address("대구광역시 달성군 체크인병합로 1")
                .latitude(35.642738)
                .longitude(128.391626)
                .userId(tourist.getId())
                .registrant(touristUsername)
                .build());
        MapPlace targetPlace = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("체크인 병합 장소")
                .address("대구광역시 달성군 체크인병합로 1")
                .latitude(35.642900)
                .longitude(128.391700)
                .userId(tourist.getId())
                .registrant(touristUsername)
                .build());
        Instant checkedInAt = Instant.parse("2026-07-20T01:00:00Z");
        locationCheckInRepository.saveAndFlush(LocationCheckIn.proximityMatched(
                tourist.getId(),
                sourcePlace.getId(),
                LocalDate.of(2026, 7, 20),
                checkedInAt,
                checkedInAt,
                10.0
        ));

        mockMvc.perform(post("/admin/places/merge")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "sourcePlaceId", sourcePlace.getId(),
                                "targetPlaceId", targetPlace.getId()
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLACE_CHECK_IN_CONNECTED"));

        assertTrue(mapPlaceRepository.existsById(sourcePlace.getId()));
        assertTrue(mapPlaceRepository.existsById(targetPlace.getId()));
        assertTrue(locationCheckInRepository.existsByPlaceId(sourcePlace.getId()));
    }

    /** 병합 이력 snapshot에서 photoCount를 제거한 구형 데이터를 복구해 게시글·북마크·추천 참조, 관광 정보와 영업 일정을 되돌리는지 확인. */
    @Test
    void restoresLegacyMergeSnapshot() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace sourcePlace = MapPlace.builder()
                .name("복구 병합 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .kakaoPlaceId("restore-source-id")
                .latitude(35.642738)
                .longitude(128.391626)
                .userId(30L)
                .registrant("sourceOwner")
                .photoCount(1L)
                .build();
        sourcePlace.updateTouristInformation(
                "Restored Tourist Place",
                "병합 복구 시 보존할 관광 정보",
                Set.of(TouristCategory.EXHIBITION, TouristCategory.NIGHTLIFE)
        );
        sourcePlace.replaceOperatingSchedule(
                Set.of(PlaceRegularOperatingHour.of(
                        DayOfWeek.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )),
                List.of(PlaceOperatingException.closed(sourcePlace, LocalDate.of(2026, 8, 15)))
        );
        sourcePlace = mapPlaceRepository.save(sourcePlace);
        MapPlace targetPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("복구 병합 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .latitude(35.642900)
                .longitude(128.391700)
                .userId(31L)
                .registrant("targetOwner")
                .photoCount(1L)
                .build());

        MapImage movedImage = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/source.jpg")
                .s3Key("map/source.jpg")
                .title("source")
                .description("source image")
                .userId(100L)
                .username("sourceUser")
                .likeCount(4L)
                .mapPlace(sourcePlace)
                .build());

        MapBookmark movedBookmark = mapBookmarkRepository.save(MapBookmark.builder()
                .userId(200L)
                .placeId(sourcePlace.getId())
                .build());
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(201L)
                .placeId(sourcePlace.getId())
                .build());
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(201L)
                .placeId(targetPlace.getId())
                .build());

        PlaceRecommendationClick movedClick = placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(sourcePlace.getId())
                .userId(300L)
                .recommendationVersion("place-rec-v1")
                .build());
        PlaceRecommendationExposure movedExposure = placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                .placeId(sourcePlace.getId())
                .userId(301L)
                .requestLatitude(35.642738)
                .requestLongitude(128.391626)
                .ranking(1)
                .recommendationVersion("place-rec-v1")
                .build());
        PlaceRecommendationConversion movedConversion =
                placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                        .placeRecommendationClickId(movedClick.getId())
                        .placeId(sourcePlace.getId())
                        .userId(400L)
                        .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                        .recommendationVersion("place-rec-v1")
                        .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(movedClick.getId())
                .placeId(sourcePlace.getId())
                .userId(401L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(movedClick.getId())
                .placeId(targetPlace.getId())
                .userId(401L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v1")
                .build());

        mockMvc.perform(post("/admin/places/merge")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of(
                                        "sourcePlaceId", sourcePlace.getId(),
                                        "targetPlaceId", targetPlace.getId()
                                )
                        )))
                .andExpect(status().isOk());

        Long historyId = adminPlaceMergeHistoryRepository.findAll().getFirst().getId();
        ObjectNode legacySourceSnapshot = (ObjectNode) objectMapper.readTree(
                adminPlaceMergeHistoryRepository.findById(historyId).orElseThrow().getSourcePlaceSnapshot()
        );
        legacySourceSnapshot.remove("photoCount");
        jdbcTemplate.update(
                "UPDATE admin_place_merge_history SET source_place_snapshot = ? WHERE id = ?",
                legacySourceSnapshot.toString(),
                historyId
        );

        mockMvc.perform(get("/admin/places/merge-histories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories[0].historyId").value(historyId))
                .andExpect(jsonPath("$.histories[0].sourcePlaceId").value(sourcePlace.getId()))
                .andExpect(jsonPath("$.histories[0].targetPlaceId").value(targetPlace.getId()))
                .andExpect(jsonPath("$.histories[0].restored").value(false));

        mockMvc.perform(post("/admin/places/merge-histories/{historyId}/restore", historyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historyId").value(historyId))
                .andExpect(jsonPath("$.sourcePlaceId").value(sourcePlace.getId()))
                .andExpect(jsonPath("$.targetPlaceId").value(targetPlace.getId()))
                .andExpect(jsonPath("$.message").value("장소 병합을 복구했습니다."));

        assertTrue(mapPlaceRepository.existsById(sourcePlace.getId()));
        assertEquals(1L, mapPlaceRepository.findById(sourcePlace.getId()).orElseThrow().currentPhotoCount());
        assertEquals(sourcePlace.getId(), mapImageRepository.findById(movedImage.getId()).orElseThrow().getMapPlace().getId());
        assertEquals(sourcePlace.getId(), mapBookmarkRepository.findById(movedBookmark.getId()).orElseThrow().getPlaceId());
        assertEquals(sourcePlace.getId(), placeRecommendationClickRepository.findById(movedClick.getId()).orElseThrow().getPlaceId());
        assertEquals(sourcePlace.getId(), placeRecommendationExposureRepository.findById(movedExposure.getId()).orElseThrow().getPlaceId());
        assertEquals(sourcePlace.getId(), placeRecommendationConversionRepository.findById(movedConversion.getId()).orElseThrow().getPlaceId());
        assertEquals(2L, mapBookmarkRepository.countByPlaceId(sourcePlace.getId()));
        assertEquals(2L, placeRecommendationConversionRepository.countConversionsByPlaceIds(List.of(sourcePlace.getId())).stream()
                .mapToLong(PlaceRecommendationConversionRepository.PlaceConversionCountProjection::getConversionCount)
                .sum());
        assertTrue(adminPlaceMergeHistoryRepository.findById(historyId).orElseThrow().isRestored());

        mockMvc.perform(get("/admin/places/{id}", sourcePlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.englishName").value("Restored Tourist Place"))
                .andExpect(jsonPath("$.touristSummary").value("병합 복구 시 보존할 관광 정보"))
                .andExpect(jsonPath(
                        "$.touristCategories",
                        containsInAnyOrder("EXHIBITION", "NIGHTLIFE")
                ))
                .andExpect(jsonPath("$.regularHours[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.operatingExceptions[0].date").value("2026-08-15"))
                .andExpect(jsonPath("$.operatingExceptions[0].closed").value(true));
    }

    /** 저장 snapshot을 smoothed CTR 순으로 조회하고 원시 CTR 및 북마크·좋아요·전체 전환율을 노출 수 기준으로 계산하는지 확인. */
    @Test
    void sortsMetricsBySmoothedCtr() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace highCtrPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("CTR 높은 장소")
                .address("경상남도 진주시 성과로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(51L)
                .registrant("metricOwner")
                .photoCount(4L)
                .build());

        MapPlace lowCtrPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("CTR 낮은 장소")
                .address("경상남도 진주시 성과로 2")
                .latitude(35.1802)
                .longitude(128.1079)
                .userId(52L)
                .registrant("metricOwner")
                .photoCount(3L)
                .build());

        java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(highCtrPlace.getId())
                .photoCount(4L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(6L)
                .bookmarkConversionCount(1L)
                .likeConversionCount(1L)
                .exposureCount(20L)
                .updatedAt(updatedAt)
                .build());
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(lowCtrPlace.getId())
                .photoCount(3L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(1L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(1L)
                .exposureCount(20L)
                .updatedAt(updatedAt.minusMinutes(5))
                .build());

        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20")
                        .param("sortBy", RecommendationMetricSortBy.SMOOTHED_CTR.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value(RecommendationMetricSortBy.SMOOTHED_CTR.name()))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.metrics[0].name").value("CTR 높은 장소"))
                .andExpect(jsonPath("$.metrics[0].exposureCount").value(20))
                .andExpect(jsonPath("$.metrics[0].clickCount").value(6))
                .andExpect(jsonPath("$.metrics[0].rawCtr").value(0.3d))
                .andExpect(jsonPath("$.metrics[0].bookmarkConversionCount").value(1))
                .andExpect(jsonPath("$.metrics[0].likeConversionCount").value(1))
                .andExpect(jsonPath("$.metrics[0].bookmarkConversionRate").value(0.05d))
                .andExpect(jsonPath("$.metrics[0].likeConversionRate").value(0.05d))
                .andExpect(jsonPath("$.metrics[0].totalConversionRate").value(0.1d))
                .andExpect(jsonPath("$.metrics[1].name").value("CTR 낮은 장소"));
    }

    /** 노출 수가 다른 두 snapshot을 총 전환율 0.2와 0.05 순으로 반환하는지 확인. */
    @Test
    void sortsMetricsByConversionRate() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace highConversionPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("전환 높은 장소")
                .address("경상남도 진주시 성과로 3")
                .latitude(35.1803)
                .longitude(128.1080)
                .userId(53L)
                .registrant("metricOwner")
                .photoCount(2L)
                .build());
        MapPlace lowConversionPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("전환 낮은 장소")
                .address("경상남도 진주시 성과로 4")
                .latitude(35.1804)
                .longitude(128.1081)
                .userId(54L)
                .registrant("metricOwner")
                .photoCount(2L)
                .build());

        java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(highConversionPlace.getId())
                .photoCount(2L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(5L)
                .bookmarkConversionCount(1L)
                .likeConversionCount(1L)
                .exposureCount(10L)
                .updatedAt(updatedAt)
                .build());
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(lowConversionPlace.getId())
                .photoCount(2L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(8L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(1L)
                .exposureCount(20L)
                .updatedAt(updatedAt)
                .build());

        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortBy", RecommendationMetricSortBy.TOTAL_CONVERSION.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value(RecommendationMetricSortBy.TOTAL_CONVERSION.name()))
                .andExpect(jsonPath("$.metrics[0].name").value("전환 높은 장소"))
                .andExpect(jsonPath("$.metrics[0].totalConversionRate").value(0.2d))
                .andExpect(jsonPath("$.metrics[1].name").value("전환 낮은 장소"))
                .andExpect(jsonPath("$.metrics[1].totalConversionRate").value(0.05d));
    }

    /** 갱신 시각 정렬에서 최신·이전 snapshot을 먼저 반환하고 snapshot 없는 장소는 마지막에 두는지 확인. */
    @Test
    void sortsMissingSnapshotsLast() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace recentPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("최근 갱신 장소")
                .address("경상남도 진주시 최신로 1")
                .latitude(35.1810)
                .longitude(128.1085)
                .userId(61L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());
        MapPlace oldPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("이전 갱신 장소")
                .address("경상남도 진주시 최신로 2")
                .latitude(35.1811)
                .longitude(128.1086)
                .userId(62L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());
        MapPlace nullUpdatedPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("미갱신 장소")
                .address("경상남도 진주시 최신로 3")
                .latitude(35.1812)
                .longitude(128.1087)
                .userId(63L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());

        LocalDateTime updatedAt = LocalDateTime.now();
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(recentPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(1L)
                .exposureCount(3L)
                .updatedAt(updatedAt)
                .build());
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(oldPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(1L)
                .exposureCount(3L)
                .updatedAt(updatedAt.minusMinutes(5))
                .build());
        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortBy", RecommendationMetricSortBy.UPDATED_AT.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value(RecommendationMetricSortBy.UPDATED_AT.name()))
                .andExpect(jsonPath("$.metrics[0].name").value("최근 갱신 장소"))
                .andExpect(jsonPath("$.metrics[1].name").value("이전 갱신 장소"))
                .andExpect(jsonPath("$.metrics[2].name").value("미갱신 장소"));
    }

    /** 버전별 이벤트를 재동기화한 후 v1 필터가 다른 버전의 전환을 제외하고 클릭 순·노출·전환 수를 반영하는지 확인. */
    @Test
    void filtersMetricsByVersion() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace versionOnePlace = mapPlaceRepository.save(MapPlace.builder()
                .name("버전1 장소")
                .address("경상남도 진주시 버전로 1")
                .latitude(35.1810)
                .longitude(128.1085)
                .userId(61L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());
        MapPlace versionTwoPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("버전2 장소")
                .address("경상남도 진주시 버전로 2")
                .latitude(35.1811)
                .longitude(128.1086)
                .userId(62L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());

        for (int index = 0; index < 10; index++) {
            placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                    .placeId(versionOnePlace.getId())
                    .userId(1000L + index)
                    .requestLatitude(35.1810)
                    .requestLongitude(128.1085)
                    .ranking(1)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        for (int index = 0; index < 4; index++) {
            placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                    .placeId(versionOnePlace.getId())
                    .userId(2000L + index)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(5001L)
                .placeId(versionOnePlace.getId())
                .userId(3001L)
                .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(5002L)
                .placeId(versionOnePlace.getId())
                .userId(3002L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v2")
                .build());

        for (int index = 0; index < 5; index++) {
            placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                    .placeId(versionTwoPlace.getId())
                    .userId(4000L + index)
                    .requestLatitude(35.1811)
                    .requestLongitude(128.1086)
                    .ranking(1)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        for (int index = 0; index < 3; index++) {
            placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                    .placeId(versionTwoPlace.getId())
                    .userId(5000L + index)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(6001L)
                .placeId(versionTwoPlace.getId())
                .userId(6001L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v1")
                .build());

        mockMvc.perform(post("/admin/places/recommendation-snapshots/resync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortBy", RecommendationMetricSortBy.CLICK.name())
                        .param("recommendationVersion", "place-rec-v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sortBy").value(RecommendationMetricSortBy.CLICK.name()))
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v1"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.metrics[0].name").value("버전1 장소"))
                .andExpect(jsonPath("$.metrics[0].exposureCount").value(10))
                .andExpect(jsonPath("$.metrics[0].clickCount").value(4))
                .andExpect(jsonPath("$.metrics[0].bookmarkConversionCount").value(1))
                .andExpect(jsonPath("$.metrics[0].likeConversionCount").value(0))
                .andExpect(jsonPath("$.metrics[1].name").value("버전2 장소"))
                .andExpect(jsonPath("$.metrics[1].exposureCount").value(5))
                .andExpect(jsonPath("$.metrics[1].clickCount").value(3))
                .andExpect(jsonPath("$.metrics[1].bookmarkConversionCount").value(0))
                .andExpect(jsonPath("$.metrics[1].likeConversionCount").value(1));
    }

    /** 최근 1일 조회는 오래된 누적 snapshot 대신 기간 내 이벤트를 집계해 과거 반응만 있는 장소의 클릭을 0으로 처리하는지 확인. */
    @Test
    void filtersMetricsByRecentDays() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace recentPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("최근 반응 장소")
                .address("경상남도 진주시 최근로 1")
                .latitude(35.1820)
                .longitude(128.1090)
                .userId(71L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());
        MapPlace staleSnapshotPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("누적 반응 장소")
                .address("경상남도 진주시 최근로 2")
                .latitude(35.1821)
                .longitude(128.1091)
                .userId(72L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());

        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(staleSnapshotPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(20L)
                .bookmarkConversionCount(3L)
                .likeConversionCount(2L)
                .exposureCount(30L)
                .updatedAt(LocalDateTime.now().minusDays(10))
                .build());

        for (int index = 0; index < 3; index++) {
            placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                    .placeId(recentPlace.getId())
                    .userId(7000L + index)
                    .requestLatitude(35.1820)
                    .requestLongitude(128.1090)
                    .ranking(1)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        for (int index = 0; index < 2; index++) {
            placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                    .placeId(recentPlace.getId())
                    .userId(7100L + index)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }

        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortBy", RecommendationMetricSortBy.CLICK.name())
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.metrics[0].name").value("최근 반응 장소"))
                .andExpect(jsonPath("$.metrics[0].clickCount").value(2))
                .andExpect(jsonPath("$.metrics[1].name").value("누적 반응 장소"))
                .andExpect(jsonPath("$.metrics[1].clickCount").value(0));
    }

    /** 기간·버전·키워드·클릭 정렬·페이지 조건을 함께 적용하고 범위 밖 이벤트를 제외한 두 번째 장소와 페이지 총계를 확인. */
    @Test
    void combinesPeriodMetricFilters() throws Exception {
        String accessToken = createAdminAndLogin();
        LocalDateTime now = LocalDateTime.now();

        MapPlace firstPlace = saveMetricPlace("기간 버전 장소 A", 91L, 35.1840, 128.1110, 3L);
        MapPlace secondPlace = saveMetricPlace("기간 버전 장소 B", 92L, 35.1841, 128.1111, 3L);
        MapPlace thirdPlace = saveMetricPlace("기간 버전 장소 C", 93L, 35.1842, 128.1112, 3L);

        seedPeriodMetric(firstPlace.getId(), "place-rec-v3", now.minusHours(2), 12, 5, 2, 1, 9000L);
        seedPeriodMetric(secondPlace.getId(), "place-rec-v3", now.minusHours(3), 9, 4, 1, 1, 9100L);
        seedPeriodMetric(thirdPlace.getId(), "place-rec-v3", now.minusHours(4), 6, 2, 0, 1, 9200L);

        seedPeriodMetric(firstPlace.getId(), "place-rec-v3", now.minusDays(5), 20, 10, 3, 2, 9300L);
        seedPeriodMetric(secondPlace.getId(), "place-rec-v2", now.minusHours(1), 30, 12, 4, 4, 9400L);

        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "2")
                        .param("limit", "1")
                        .param("sortBy", RecommendationMetricSortBy.CLICK.name())
                        .param("recommendationVersion", "place-rec-v3")
                        .param("days", "1")
                        .param("keyword", "기간 버전 장소"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.sortBy").value(RecommendationMetricSortBy.CLICK.name()))
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v3"))
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.metrics", hasSize(1)))
                .andExpect(jsonPath("$.metrics[0].name").value("기간 버전 장소 B"))
                .andExpect(jsonPath("$.metrics[0].exposureCount").value(9))
                .andExpect(jsonPath("$.metrics[0].clickCount").value(4))
                .andExpect(jsonPath("$.metrics[0].bookmarkConversionCount").value(1))
                .andExpect(jsonPath("$.metrics[0].likeConversionCount").value(1));
    }

    /** 클릭 수가 순차 감소하는 다섯 장소에서 크기 2의 두 번째 페이지가 세 번째·네 번째 장소를 반환하는지 확인. */
    @Test
    void paginatesPeriodClickMetrics() throws Exception {
        String accessToken = createAdminAndLogin();
        LocalDateTime now = LocalDateTime.now();

        for (int index = 0; index < 5; index++) {
            MapPlace place = saveMetricPlace(
                    "페이지 장소 " + (index + 1),
                    100L + index,
                    35.1850 + (index * 0.001d),
                    128.1120 + (index * 0.001d),
                    2L
            );
            seedPeriodMetric(place.getId(), "place-rec-v4", now.minusMinutes(index + 1), 10 + index, 10 - index, 0, 0, 10000L + (index * 100));
        }

        mockMvc.perform(get("/admin/places/recommendation-metrics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "2")
                        .param("limit", "2")
                        .param("sortBy", RecommendationMetricSortBy.CLICK.name())
                        .param("recommendationVersion", "place-rec-v4")
                        .param("days", "1")
                        .param("keyword", "페이지 장소"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.metrics", hasSize(2)))
                .andExpect(jsonPath("$.metrics[0].name").value("페이지 장소 3"))
                .andExpect(jsonPath("$.metrics[0].clickCount").value(8))
                .andExpect(jsonPath("$.metrics[1].name").value("페이지 장소 4"))
                .andExpect(jsonPath("$.metrics[1].clickCount").value(7));
    }

    /** 두 추천 버전의 최근 노출·클릭·전환 요약과 target-minus-baseline 차이를 HTTP 응답으로 확인. */
    @Test
    void comparesVersionMetricDeltas() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace comparePlace = mapPlaceRepository.save(MapPlace.builder()
                .name("비교 장소")
                .address("경상남도 진주시 비교로 1")
                .latitude(35.1830)
                .longitude(128.1100)
                .userId(81L)
                .registrant("metricOwner")
                .photoCount(1L)
                .build());

        for (int index = 0; index < 10; index++) {
            placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                    .placeId(comparePlace.getId())
                    .userId(8000L + index)
                    .requestLatitude(35.1830)
                    .requestLongitude(128.1100)
                    .ranking(1)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        for (int index = 0; index < 4; index++) {
            placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                    .placeId(comparePlace.getId())
                    .userId(8100L + index)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(9001L)
                .placeId(comparePlace.getId())
                .userId(8201L)
                .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                .recommendationVersion("place-rec-v1")
                .build());

        for (int index = 0; index < 12; index++) {
            placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                    .placeId(comparePlace.getId())
                    .userId(8300L + index)
                    .requestLatitude(35.1830)
                    .requestLongitude(128.1100)
                    .ranking(1)
                    .recommendationVersion("place-rec-v2")
                    .build());
        }
        for (int index = 0; index < 6; index++) {
            placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                    .placeId(comparePlace.getId())
                    .userId(8400L + index)
                    .recommendationVersion("place-rec-v2")
                    .build());
        }
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(9002L)
                .placeId(comparePlace.getId())
                .userId(8501L)
                .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                .recommendationVersion("place-rec-v2")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(9003L)
                .placeId(comparePlace.getId())
                .userId(8502L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v2")
                .build());

        mockMvc.perform(get("/admin/places/recommendation-metrics/compare")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("baselineVersion", "place-rec-v1")
                        .param("targetVersion", "place-rec-v2")
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineVersion").value("place-rec-v1"))
                .andExpect(jsonPath("$.targetVersion").value("place-rec-v2"))
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.baseline.exposureCount").value(10))
                .andExpect(jsonPath("$.baseline.clickCount").value(4))
                .andExpect(jsonPath("$.target.exposureCount").value(12))
                .andExpect(jsonPath("$.target.clickCount").value(6))
                .andExpect(jsonPath("$.target.likeConversionCount").value(1))
                .andExpect(jsonPath("$.delta.exposureCount").value(2))
                .andExpect(jsonPath("$.delta.clickCount").value(2))
                .andExpect(jsonPath("$.delta.bookmarkConversionCount").value(0))
                .andExpect(jsonPath("$.delta.likeConversionCount").value(1));
    }

    /** 현재 장소의 사진·좋아요·노출·클릭·전환을 다시 집계하고 존재하지 않는 장소의 일반·유사도 snapshot을 제거하는지 확인. */
    @Test
    void rebuildsSnapshotsAndRemovesOrphans() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("재동기화 장소")
                .address("경상남도 진주시 재동기화로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(41L)
                .registrant("resyncOwner")
                .photoCount(2L)
                .build());

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(100L)
                .placeId(mapPlace.getId())
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/resync-1.jpg")
                .s3Key("map/resync-1.jpg")
                .title("재동기화 사진 1")
                .description("첫 번째 사진")
                .userId(41L)
                .username("resyncOwner")
                .likeCount(4L)
                .mapPlace(mapPlace)
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/resync-2.jpg")
                .s3Key("map/resync-2.jpg")
                .title("재동기화 사진 2")
                .description("두 번째 사진")
                .userId(41L)
                .username("resyncOwner")
                .likeCount(6L)
                .mapPlace(mapPlace)
                .build());

        placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                .placeId(mapPlace.getId())
                .userId(300L)
                .requestLatitude(35.1801)
                .requestLongitude(128.1078)
                .ranking(1)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                .placeId(mapPlace.getId())
                .userId(301L)
                .requestLatitude(35.1801)
                .requestLongitude(128.1078)
                .ranking(2)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(mapPlace.getId())
                .userId(400L)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(4001L)
                .placeId(mapPlace.getId())
                .userId(401L)
                .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                .recommendationVersion("place-rec-v1")
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(4002L)
                .placeId(mapPlace.getId())
                .userId(402L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
                .recommendationVersion("place-rec-v1")
                .build());

        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(9999L)
                .photoCount(99L)
                .bookmarkCount(99L)
                .totalLikeCount(99L)
                .clickCount(99L)
                .bookmarkConversionCount(99L)
                .likeConversionCount(99L)
                .exposureCount(99L)
                .updatedAt(java.time.LocalDateTime.now())
                .build());
        placeSimilaritySnapshotRepository.save(PlaceSimilaritySnapshot.builder()
                .leftPlaceId(9998L)
                .rightPlaceId(9999L)
                .geoKernelScore(0.9d)
                .coBookmarkPmiScore(0.9d)
                .coLikeCosineScore(0.9d)
                .trendSimilarityScore(0.9d)
                .totalSimilarityScore(0.9d)
                .updatedAt(java.time.LocalDateTime.now())
                .build());

        mockMvc.perform(post("/admin/places/recommendation-snapshots/resync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(1))
                .andExpect(jsonPath("$.synchronizedSnapshotCount").value(1))
                .andExpect(jsonPath("$.deletedSnapshotCount").value(1))
                .andExpect(jsonPath("$.synchronizedSimilaritySnapshotCount").value(0))
                .andExpect(jsonPath("$.deletedSimilaritySnapshotCount").value(1))
                .andExpect(jsonPath("$.synchronizedVersionSnapshotCount").value(1))
                .andExpect(jsonPath("$.deletedVersionSnapshotCount").value(0))
                .andExpect(jsonPath("$.message").value("장소 추천 snapshot 재동기화를 완료했습니다."));

        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(2L, snapshot.getPhotoCount());
        assertEquals(1L, snapshot.getBookmarkCount());
        assertEquals(10L, snapshot.getTotalLikeCount());
        assertEquals(1L, snapshot.getClickCount());
        assertEquals(1L, snapshot.getBookmarkConversionCount());
        assertEquals(1L, snapshot.getLikeConversionCount());
        assertEquals(2L, snapshot.getExposureCount());
        assertNotNull(snapshot.getLatestPostCreatedAt());
        assertFalse(placeRecommendationSnapshotRepository.existsById(9999L));
        assertEquals(0L, placeSimilaritySnapshotRepository.count());
    }

    /** 인접한 두 장소를 재동기화하면 유사도 쌍 하나가 저장되고 응답 건수와 일치하는지 확인. */
    @Test
    void rebuildsPlaceSimilaritySnapshot() throws Exception {
        String accessToken = createAdminAndLogin();

        mapPlaceRepository.save(MapPlace.builder()
                .name("유사도 기준 장소 1")
                .address("경상남도 진주시 유사도로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(901L)
                .registrant("similarityOwner")
                .photoCount(3L)
                .build());
        mapPlaceRepository.save(MapPlace.builder()
                .name("유사도 기준 장소 2")
                .address("경상남도 진주시 유사도로 2")
                .latitude(35.1803)
                .longitude(128.1080)
                .userId(902L)
                .registrant("similarityOwner")
                .photoCount(5L)
                .build());

        mockMvc.perform(post("/admin/places/recommendation-snapshots/resync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.synchronizedSimilaritySnapshotCount").value(1))
                .andExpect(jsonPath("$.deletedSimilaritySnapshotCount").value(0));

        assertEquals(1L, placeSimilaritySnapshotRepository.count());
    }

    /** 증가하는 이름의 관리자 계정을 생성하고 실제 로그인 API로 access token을 발급받음. */
    private String createAdminAndLogin() throws Exception {
        String username = "adminPlaceTester" + ADMIN_SEQUENCE.incrementAndGet();
        return createUserAndLogin(username, UserRole.ADMIN);
    }

    /** 지정 역할 계정을 DB에 저장하고 HTTP 로그인 성공 응답의 access token을 반환. refresh token 등 로그인 부작용도 발생. */
    private String createUserAndLogin(String username, UserRole role) throws Exception {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());

        LoginRequest loginRequest = new LoginRequest(username, "password123");
        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }

    /** 지정 전후 노출 상태의 누적 카운터를 읽음. 미등록이면 0으로 취급해 요청 전후 증가량을 비교. */
    private double discoveryStatusUpdateCount(PlaceDiscoveryStatus fromStatus, PlaceDiscoveryStatus toStatus) {
        var counter = meterRegistry.find("pingdom.place.discovery_status_updates")
                .tag("from_status", fromStatus.name())
                .tag("to_status", toStatus.name())
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    /** 출처별 근거 제출 카운터를 읽고 아직 생성되지 않은 계측기는 0으로 반환. */
    private double informationEvidenceSubmittedCount(PlaceInformationSourceType sourceType) {
        var counter = meterRegistry.find("pingdom.place.information_evidence_submitted")
                .tag("source_type", sourceType.name())
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    /** 검증 상태 전이 카운터의 현재값을 읽어 공유 registry의 누적값에 의존하지 않고 증가분을 검사. */
    private double informationVerificationStatusUpdateCount(
            PlaceInformationVerificationStatus fromStatus,
            PlaceInformationVerificationStatus toStatus
    ) {
        var counter = meterRegistry.find("pingdom.place.information_verification_status_updates")
                .tag("from_status", fromStatus.name())
                .tag("to_status", toStatus.name())
                .counter();
        return counter == null ? 0.0d : counter.count();
    }

    /** 추천 지표 비교에 필요한 장소명·등록자·좌표·사진 수를 실제 저장소에 저장. */
    private MapPlace saveMetricPlace(String name, Long userId, double latitude, double longitude, Long photoCount) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address("경상남도 진주시 테스트로 " + userId)
                .latitude(latitude)
                .longitude(longitude)
                .userId(userId)
                .registrant("metricOwner")
                .photoCount(photoCount)
                .build());
    }

    /** 지정 시각부터 초 단위로 차이를 둔 노출·클릭·북마크/좋아요 전환을 DB에 저장. sequenceSeed로 사용자와 연결 식별자 충돌을 회피. */
    private void seedPeriodMetric(
            Long placeId,
            String recommendationVersion,
            LocalDateTime createdAt,
            int exposureCount,
            int clickCount,
            int bookmarkConversionCount,
            int likeConversionCount,
            long sequenceSeed
    ) {
        List<PlaceRecommendationExposure> exposures = new java.util.ArrayList<>();
        for (int index = 0; index < exposureCount; index++) {
            exposures.add(PlaceRecommendationExposure.builder()
                    .placeId(placeId)
                    .userId(sequenceSeed + index)
                    .requestLatitude(35.1800)
                    .requestLongitude(128.1070)
                    .ranking(1)
                    .recommendationVersion(recommendationVersion)
                    .createdAt(createdAt.plusSeconds(index))
                    .build());
        }
        placeRecommendationExposureRepository.saveAll(exposures);

        List<PlaceRecommendationClick> clicks = new java.util.ArrayList<>();
        for (int index = 0; index < clickCount; index++) {
            clicks.add(PlaceRecommendationClick.builder()
                    .placeId(placeId)
                    .userId(sequenceSeed + 1_000 + index)
                    .recommendationVersion(recommendationVersion)
                    .createdAt(createdAt.plusSeconds(index))
                    .build());
        }
        placeRecommendationClickRepository.saveAll(clicks);

        List<PlaceRecommendationConversion> conversions = new java.util.ArrayList<>();
        for (int index = 0; index < bookmarkConversionCount; index++) {
            conversions.add(PlaceRecommendationConversion.builder()
                    .placeRecommendationClickId(sequenceSeed + 2_000 + index)
                    .placeId(placeId)
                    .userId(sequenceSeed + 3_000 + index)
                    .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                    .recommendationVersion(recommendationVersion)
                    .createdAt(createdAt.plusSeconds(index))
                    .build());
        }
        for (int index = 0; index < likeConversionCount; index++) {
            conversions.add(PlaceRecommendationConversion.builder()
                    .placeRecommendationClickId(sequenceSeed + 4_000 + index)
                    .placeId(placeId)
                    .userId(sequenceSeed + 5_000 + index)
                    .conversionType(PlaceRecommendationConversionType.LIKE)
                    .recommendationVersion(recommendationVersion)
                    .createdAt(createdAt.plusSeconds(index))
                    .build());
        }
        placeRecommendationConversionRepository.saveAll(conversions);
    }
}
