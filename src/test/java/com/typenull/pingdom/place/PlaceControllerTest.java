package com.typenull.pingdom.place;

import com.typenull.pingdom.place.api.PlaceController;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import com.typenull.pingdom.place.domain.place.media.PlaceMedia;
import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationExposure;
import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceMediaRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "abuse.rate-limit.redis-key-prefix=pingdom:test:place-controller:",
        "abuse.rate-limit.signup-ip.limit=1000",
        "abuse.rate-limit.login-ip.limit=1000"
})
@AutoConfigureMockMvc
@Transactional
class PlaceControllerTest {

    @TestConfiguration
    static class TestEmailSenderConfig {
        @Bean
        @Primary
        EmailSender emailSender() {
            return (recipientEmail, verificationCode) -> EmailSendResult.sent(null);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlaceController placeController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private PlaceMediaRepository placeMediaRepository;

    @Autowired
    private MapImageLikeRepository mapImageLikeRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Autowired
    private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;

    @Autowired
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Autowired
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Autowired
    private PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        placeMediaRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        placeRecommendationConversionRepository.deleteAllInBatch();
        placeRecommendationClickRepository.deleteAllInBatch();
        placeRecommendationExposureRepository.deleteAllInBatch();
        placeRecommendationFeatureLogRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        clearOperatingScheduleRows();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDownOperatingScheduleRows() {
        clearOperatingScheduleRows();
    }

    private void clearOperatingScheduleRows() {
        jdbcTemplate.update("DELETE FROM map_place_operating_exception_hour");
        jdbcTemplate.update("DELETE FROM map_place_operating_exception");
        jdbcTemplate.update("DELETE FROM map_place_regular_operating_hour");
    }

    @Test
    void listPlacesReturnsPagedPlaces() throws Exception {
        String accessToken = signupAndLogin("reader01");
        createMapPlace("첫 번째 장소", "경상남도 진주시 진양호로 1");
        createMapPlace("두 번째 장소", "경상남도 진주시 남강로 2");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].name").value("두 번째 장소"))
                .andExpect(jsonPath("$.places[1].name").value("첫 번째 장소"));
    }

    @Test
    void nonOperatingPlacesAreHiddenFromPublicPlaceQueries() throws Exception {
        String accessToken = signupAndLogin("operatingStatusReader");
        User user = userRepository.findByUsername("operatingStatusReader").orElseThrow();
        MapPlace operatingPlace = createMapPlace("운영 중 장소", "경상남도 진주시 운영로 1");
        MapPlace closedPlace = createMapPlace("임시 휴업 장소", "경상남도 진주시 운영로 2");
        closedPlace.updateOperatingStatus(
                PlaceOperatingStatus.TEMPORARILY_CLOSED,
                LocalDateTime.of(2026, 7, 13, 10, 30)
        );
        mapPlaceRepository.saveAndFlush(closedPlace);
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(user.getId())
                .placeId(closedPlace.getId())
                .build());

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places[0].id").value(operatingPlace.getId()))
                .andExpect(jsonPath("$.places[0].operatingStatus").value("OPERATING"));

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places[0].id").value(operatingPlace.getId()));

        mockMvc.perform(get("/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "휴업"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));

        mockMvc.perform(get("/places/{id}", closedPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void recommendationsExcludeNonOperatingPlaces() {
        MapPlace closedPlace = createMapPlace("추천 제외 장소", "경상남도 진주시 추천로 1", 35.1801, 128.1078, 1L);
        closedPlace.updateOperatingStatus(
                PlaceOperatingStatus.PERMANENTLY_CLOSED,
                LocalDateTime.of(2026, 7, 13, 10, 30)
        );
        mapPlaceRepository.saveAndFlush(closedPlace);

        var response = placeController.recommendPlaces(35.1801, 128.1078, 1, 5.0, null, null);

        assertEquals(0, response.getBody().recommendedCount());
    }

    @Test
    void listPlacesSupportsMaximumLimitWithoutKeyword() throws Exception {
        String accessToken = signupAndLogin("readerLimit100");
        for (int index = 1; index <= 101; index++) {
            createMapPlace("목록 장소 " + index, "경상남도 진주시 목록로 " + index);
        }

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.totalCount").value(101))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.places.length()").value(100))
                .andExpect(jsonPath("$.places[0].name").value("목록 장소 101"))
                .andExpect(jsonPath("$.places[99].name").value("목록 장소 2"));
    }

    @Test
    void listPlacesSupportsMaximumLimitWithKeyword() throws Exception {
        String accessToken = signupAndLogin("readerKeywordLimit100");
        MapPlace matchingPlace = createMapPlace("테스트 장소", "경상남도 진주시 테스트로 1");
        createMapPlace("일반 장소", "경상남도 진주시 일반로 1");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "테스트")
                        .param("page", "1")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(matchingPlace.getId()));
    }

    @Test
    void listPlacesExcludesPlacesWithInvalidCoordinates() throws Exception {
        String accessToken = signupAndLogin("readerInvalidCoordinate");
        MapPlace validPlace = createMapPlace("정상 좌표 장소", "경상남도 진주시 정상로 1");
        mapPlaceRepository.save(MapPlace.builder()
                .name("지도 표시 불가 장소")
                .address("경상남도 진주시 이상로 1")
                .latitude(91.0)
                .longitude(128.1078)
                .userId(1L)
                .registrant("placeOwner")
                .photoCount(0L)
                .build());

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(validPlace.getId()));
    }

    @Test
    void listPlacesSearchesByAddressAndCategory() throws Exception {
        String accessToken = signupAndLogin("readerSearch" + Long.toUnsignedString(System.nanoTime()));
        MapPlace matchingPlace = createMapPlace(
                "진주성",
                "진주성 대표 주소",
                "관광",
                35.1894,
                128.0789
        );
        matchingPlace.updateGeocoding(
                "경상남도 진주시 남강로 626",
                "경상남도 진주시 남강로 626",
                "경상남도 진주시 본성동 500-8",
                "52692",
                matchingPlace.getLatitude(),
                matchingPlace.getLongitude(),
                matchingPlace.getLocation(),
                GeocodingSource.KAKAO
        );
        mapPlaceRepository.save(matchingPlace);
        createMapPlace("남강 카페", "경상남도 진주시 남강로 10", "카페", 35.1801, 128.1078);

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "남강로")
                        .param("category", " 관광 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(matchingPlace.getId()))
                .andExpect(jsonPath("$.places[0].roadAddress").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.places[0].jibunAddress").value("경상남도 진주시 본성동 500-8"))
                .andExpect(jsonPath("$.places[0].postalCode").value("52692"))
                .andExpect(jsonPath("$.places[0].geocodingSource").value("KAKAO"))
                .andExpect(jsonPath("$.places[0].category").value("관광"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void listPlacesFiltersByTouristCategory() throws Exception {
        String accessToken = signupAndLogin("readerTouristCategory" + Long.toUnsignedString(System.nanoTime()));
        MapPlace kpopPlace = createMapPlace("케이팝 명소", "서울특별시 중구 케이팝로 1", "관광", 37.5665, 126.9780);
        kpopPlace.updateTouristInformation(
                "K-pop Spot",
                "K-pop tourists visit here.",
                Set.of(TouristCategory.K_POP)
        );
        mapPlaceRepository.save(kpopPlace);
        MapPlace cafePlace = createMapPlace("관광 카페", "서울특별시 중구 카페로 1", "카페", 37.5670, 126.9790);
        cafePlace.updateTouristInformation(
                "Tour Cafe",
                "Cafe for tourists.",
                Set.of(TouristCategory.CAFE)
        );
        mapPlaceRepository.saveAndFlush(cafePlace);

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("touristCategory", " k_pop "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(kpopPlace.getId()))
                .andExpect(jsonPath("$.places[0].touristCategories[0]").value("K_POP"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void listPlacesSortsPopularByPhotoCount() throws Exception {
        String accessToken = signupAndLogin("readerPopularSort" + Long.toUnsignedString(System.nanoTime()));
        createMapPlace("덜 인기 장소", "경상남도 진주시 인기고요로 1", "카페", 35.1801, 128.1078, 1L);
        MapPlace popularPlace = createMapPlace(
                "인기 장소",
                "경상남도 진주시 인기많음로 1",
                "카페",
                35.1802,
                128.1079,
                9L
        );

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sort", "POPULAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].id").value(popularPlace.getId()))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void hiddenDiscoveryPlacesAreExcludedFromPublicPlaceQueries() throws Exception {
        String username = "readerHiddenDiscovery" + Long.toUnsignedString(System.nanoTime());
        String accessToken = signupAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        MapPlace visiblePlace = createMapPlace("탐색 노출 장소", "경상남도 진주시 노출로 1");
        MapPlace hiddenPlace = createMapPlace("탐색 숨김 장소", "경상남도 진주시 숨김로 1");
        hiddenPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(hiddenPlace);
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(user.getId())
                .placeId(hiddenPlace.getId())
                .build());

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.places[0].id").value(visiblePlace.getId()));

        mockMvc.perform(get("/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "숨김"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));

        mockMvc.perform(get("/places/{id}", hiddenPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void touristPlaceCardReturnsDecisionReadySummaryAndHidesNonPublicPlaces() throws Exception {
        String accessToken = signupAndLogin("touristPlaceCard" + Long.toUnsignedString(System.nanoTime()));
        MapPlace visiblePlace = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("서울 K-컬처 스튜디오")
                .englishName("Seoul K-Culture Studio")
                .imageUrl("https://cdn.pingdom.test/studio.jpg")
                .address("서울특별시 중구 문화로 1")
                .roadAddress("서울특별시 중구 문화로 1")
                .category("전시")
                .latitude(37.5665)
                .longitude(126.9780)
                .userId(1L)
                .registrant("placeOwner")
                .build());
        visiblePlace.updateTouristInformation(
                "Seoul K-Culture Studio",
                "A verified K-culture experience for international visitors.",
                Set.of(TouristCategory.EXHIBITION)
        );
        mapPlaceRepository.saveAndFlush(visiblePlace);

        mockMvc.perform(get("/places/{placeId}/card", visiblePlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visiblePlace.getId()))
                .andExpect(jsonPath("$.name").value("서울 K-컬처 스튜디오"))
                .andExpect(jsonPath("$.englishName").value("Seoul K-Culture Studio"))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.pingdom.test/studio.jpg"))
                .andExpect(jsonPath("$.address").value("서울특별시 중구 문화로 1"))
                .andExpect(jsonPath("$.touristSummary").value(
                        "A verified K-culture experience for international visitors."))
                .andExpect(jsonPath("$.touristCategories").value(containsInAnyOrder("EXHIBITION")))
                .andExpect(jsonPath("$.latitude").value(37.5665))
                .andExpect(jsonPath("$.longitude").value(126.9780))
                .andExpect(jsonPath("$.operatingStatus").value("OPERATING"))
                .andExpect(jsonPath("$.currentlyOperating").value(false))
                .andExpect(jsonPath("$.currentlyOperatingCheckedAt").isNotEmpty())
                .andExpect(jsonPath("$.primaryInformationSource").value("LEGACY"))
                .andExpect(jsonPath("$.informationVerificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.verifiedEvidenceCount").value(0))
                .andExpect(jsonPath("$.lastVerifiedAt").doesNotExist());

        visiblePlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(visiblePlace);

        mockMvc.perform(get("/places/{placeId}/card", visiblePlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        visiblePlace.updateDiscoveryStatus(PlaceDiscoveryStatus.VISIBLE);
        visiblePlace.updateOperatingStatus(PlaceOperatingStatus.TEMPORARILY_CLOSED, LocalDateTime.now());
        mapPlaceRepository.saveAndFlush(visiblePlace);

        mockMvc.perform(get("/places/{placeId}/card", visiblePlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatingStatus").value("TEMPORARILY_CLOSED"))
                .andExpect(jsonPath("$.currentlyOperating").value(false));

        visiblePlace.updateOperatingStatus(PlaceOperatingStatus.PERMANENTLY_CLOSED, LocalDateTime.now());
        mapPlaceRepository.saveAndFlush(visiblePlace);

        mockMvc.perform(get("/places/{placeId}/card", visiblePlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void touristPlaceCardRequiresAuthenticationAndReturnsNotFoundForUnknownPlace() throws Exception {
        mockMvc.perform(get("/places/{placeId}/card", 999_999_999L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String accessToken = signupAndLogin("touristPlaceCardFailure" + Long.toUnsignedString(System.nanoTime()));

        mockMvc.perform(get("/places/{placeId}/card", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void listPlacesFiltersByRadiusAndSortsNearest() throws Exception {
        String accessToken = signupAndLogin("readerSearch02");
        MapPlace nearPlace = createMapPlace("가까운 장소", "경상남도 진주시 가까운로 1", "카페", 35.1802, 128.1079);
        createMapPlace("먼저 생성된 먼 장소", "경상남도 진주시 먼로 1", "카페", 35.1840, 128.1110);
        createMapPlace("반경 밖 장소", "경상남도 진주시 바깥로 1", "카페", 35.2500, 128.2000);

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("radiusKm", "1.0")
                        .param("sort", "NEAREST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].id").value(nearPlace.getId()))
                .andExpect(jsonPath("$.places[0].distanceMeters").isNumber())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void listPlacesRejectsIncompleteDistanceCondition() throws Exception {
        String accessToken = signupAndLogin("readerSearch03");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_CONDITION_INVALID"));
    }

    @Test
    void listPlacesRejectsNearestWithoutDistanceCondition() throws Exception {
        String accessToken = signupAndLogin("readerSearch04");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sort", "NEAREST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_CONDITION_INVALID"));
    }

    @Test
    void listPlacesRejectsNonFiniteDistanceCondition() throws Exception {
        String accessToken = signupAndLogin("readerSearch05");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "Infinity")
                        .param("radiusKm", "1.0")
                        .param("sort", "NEAREST"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPlacesRejectsUnsupportedSort() throws Exception {
        String accessToken = signupAndLogin("readerUnsupportedSort" + Long.toUnsignedString(System.nanoTime()));

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sort", "RATING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PLACE_SEARCH_SORT"));
    }

    @Test
    void listPlacesRejectsUnsupportedTouristCategory() throws Exception {
        String accessToken = signupAndLogin("readerUnsupportedTouristCategory" + Long.toUnsignedString(System.nanoTime()));

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("touristCategory", "NOT_A_TOURIST_CATEGORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_CONDITION_INVALID"));
    }

    @Test
    void listPlacesReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/places"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void getPlaceReturnsPlaceDetailOnly() throws Exception {
        String accessToken = signupAndLogin("reader02");
        MapPlace mapPlace = createMapPlace("진주성", "경상남도 진주시 남강로 626");

        mockMvc.perform(get("/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapPlace.getId()))
                .andExpect(jsonPath("$.name").value("진주성"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.registrant").value("placeOwner"));
    }

    @Test
    void getPlaceVisitDecisionReturnsEmptySupplementalDataWhenNoneExists() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReader01");
        MapPlace mapPlace = createMapPlace("방문 결정 장소", "경상남도 진주시 방문로 1");

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.id").value(mapPlace.getId()))
                .andExpect(jsonPath("$.place.name").value("방문 결정 장소"))
                .andExpect(jsonPath("$.merchantInformation").isEmpty())
                .andExpect(jsonPath("$.ongoingEvents.length()").value(0))
                .andExpect(jsonPath("$.reservableAvailabilities.length()").value(0))
                .andExpect(jsonPath("$.availableOffers.offers.length()").value(0))
                .andExpect(jsonPath("$.checkedAt").isNotEmpty());
    }

    @Test
    void getPlaceVisitDecisionKeepsTemporarilyClosedPlaceVisible() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReader02");
        MapPlace mapPlace = createMapPlace("임시 휴업 방문 결정 장소", "경상남도 진주시 방문로 2");
        mapPlace.updateOperatingStatus(
                PlaceOperatingStatus.TEMPORARILY_CLOSED,
                LocalDateTime.of(2026, 8, 5, 9, 0)
        );
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.operatingStatus").value("TEMPORARILY_CLOSED"))
                .andExpect(jsonPath("$.place.currentlyOperating").value(false));
    }

    @Test
    void getPlaceVisitDecisionRejectsPermanentlyClosedPlace() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReader03");
        MapPlace mapPlace = createMapPlace("영구 폐업 방문 결정 장소", "경상남도 진주시 방문로 3");
        mapPlace.updateOperatingStatus(
                PlaceOperatingStatus.PERMANENTLY_CLOSED,
                LocalDateTime.of(2026, 8, 5, 9, 0)
        );
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void getPlaceVisitDecisionRejectsUnauthenticatedRequest() throws Exception {
        MapPlace mapPlace = createMapPlace("인증 필요 방문 결정 장소", "경상남도 진주시 방문로 4");

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void getPlaceVisitDecisionRejectsHiddenPlace() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReader04");
        MapPlace mapPlace = createMapPlace("숨김 방문 결정 장소", "경상남도 진주시 방문로 5");
        mapPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void listBookmarksReturnsOnlyBookmarkedPlaces() throws Exception {
        String accessToken = signupAndLogin("bookmarkReader01");
        User user = userRepository.findByUsername("bookmarkReader01").orElseThrow();

        MapPlace firstBookmarkedPlace = createMapPlace("첫 번째 북마크 장소", "경상남도 진주시 북마크로 1");
        MapPlace secondBookmarkedPlace = createMapPlace("두 번째 북마크 장소", "경상남도 진주시 북마크로 2");
        createMapPlace("북마크되지 않은 장소", "경상남도 진주시 북마크로 3");

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(user.getId())
                .placeId(firstBookmarkedPlace.getId())
                .build());
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(user.getId())
                .placeId(secondBookmarkedPlace.getId())
                .build());

        mockMvc.perform(get("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].name").value("두 번째 북마크 장소"))
                .andExpect(jsonPath("$.places[1].name").value("첫 번째 북마크 장소"));
    }

    @Test
    void listBookmarksReturnsEmptyListWhenNoBookmarkExists() throws Exception {
        String accessToken = signupAndLogin("bookmarkReader02");
        createMapPlace("일반 장소", "경상남도 진주시 북마크로 4");

        mockMvc.perform(get("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void placeAndBookmarkLegacyPathsRemainSupported() throws Exception {
        String accessToken = signupAndLogin("legacyPathReader01");
        User user = userRepository.findByUsername("legacyPathReader01").orElseThrow();
        MapPlace bookmarkedPlace = createMapPlace("레거시 북마크 장소", "경상남도 진주시 레거시로 1");
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(user.getId())
                .placeId(bookmarkedPlace.getId())
                .build());

        mockMvc.perform(get("/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("레거시 북마크 장소"));

        mockMvc.perform(get("/users/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("레거시 북마크 장소"));
    }

    @Test
    void legacyPlaceDetailPathRemainsSupportedAfterCompatibilityPackageMigration() throws Exception {
        String accessToken = signupAndLogin("legacyPlaceDetail" + Long.toUnsignedString(System.nanoTime()));
        MapPlace place = createMapPlace("구 장소 상세", "경상남도 진주시 호환로 1");

        mockMvc.perform(get("/place/{id}", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(place.getId()))
                .andExpect(jsonPath("$.name").value("구 장소 상세"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 호환로 1"));
    }

    @Test
    void uploadPlaceStoresImageUrl() throws Exception {
        String accessToken = signupAndLogin("placeUploader01");
        String coordinateToken = createCoordinateToken(accessToken, "27414316", 35.1801, 128.1078);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kakaoPlaceId", "27414316",
                                "name", "이미지 포함 장소",
                                "address", "경상남도 진주시 이미지로 1",
                                "category", "카페",
                                "imageUrl", "https://example.com/images/place-upload.jpg",
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("이미지 포함 장소"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 이미지로 1"));

        MapPlace saved = mapPlaceRepository.findByKakaoPlaceId("27414316").orElseThrow();
        assertEquals("카페", saved.getCategory());
        assertEquals("https://example.com/images/place-upload.jpg", saved.getImageUrl());
    }

    @Test
    void uploadPlaceStoresAndReturnsTouristInformation() throws Exception {
        String accessToken = signupAndLogin("placeTourist" + Long.toUnsignedString(System.nanoTime()));
        String coordinateToken = createCoordinateToken(accessToken, "27414320", 35.1805, 128.1082);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("kakaoPlaceId", "27414320"),
                                Map.entry("name", "관광 정보 장소"),
                                Map.entry("address", "경상남도 진주시 관광로 1"),
                                Map.entry("roadAddress", "경상남도 진주시 관광로 1"),
                                Map.entry("jibunAddress", "경상남도 진주시 관광동 10"),
                                Map.entry("postalCode", "52692"),
                                Map.entry("category", "관광"),
                                Map.entry("englishName", "  Jinju Tourist Place  "),
                                Map.entry("touristSummary", "  외국인 관광객을 위한 장소 요약입니다.  "),
                                Map.entry("touristCategories", List.of("K_POP", "EXHIBITION")),
                                Map.entry("coordinateToken", coordinateToken)
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roadAddress").value("경상남도 진주시 관광로 1"))
                .andExpect(jsonPath("$.jibunAddress").value("경상남도 진주시 관광동 10"))
                .andExpect(jsonPath("$.postalCode").value("52692"))
                .andExpect(jsonPath("$.geocodingSource").value("USER_PIN"))
                .andExpect(jsonPath("$.englishName").value("Jinju Tourist Place"))
                .andExpect(jsonPath("$.touristSummary").value("외국인 관광객을 위한 장소 요약입니다."))
                .andExpect(jsonPath("$.touristCategories", containsInAnyOrder("K_POP", "EXHIBITION")));

        MapPlace saved = mapPlaceRepository.findByKakaoPlaceId("27414320").orElseThrow();
        assertEquals("Jinju Tourist Place", saved.getEnglishName());
        assertEquals("외국인 관광객을 위한 장소 요약입니다.", saved.getTouristSummary());
        assertEquals(
                Set.of(TouristCategory.K_POP, TouristCategory.EXHIBITION),
                saved.currentTouristCategories()
        );
        mapPlaceRepository.flush();
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_tourist_guard WHERE map_place_id = ?",
                Integer.class,
                saved.getId()
        ));
    }

    @Test
    void uploadPlaceRejectsNullTouristCategoryElement() throws Exception {
        String accessToken = signupAndLogin("placeUploaderTourist02");
        String coordinateToken = createCoordinateToken(accessToken, "27414321", 35.1806, 128.1083);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kakaoPlaceId": "27414321",
                                  "name": "잘못된 관광 정보 장소",
                                  "address": "경상남도 진주시 관광로 2",
                                  "category": "관광",
                                  "touristCategories": ["CAFE", null],
                                  "coordinateToken": "%s"
                                }
                                """.formatted(coordinateToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listDetailAndAutocompleteExposeTouristInformationAndSearchEnglishName() throws Exception {
        String accessToken = signupAndLogin("readerTourist01");
        MapPlace touristPlace = createTouristMapPlace();

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "jinju castle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(touristPlace.getId()))
                .andExpect(jsonPath("$.places[0].englishName").value("Jinju Castle"))
                .andExpect(jsonPath("$.places[0].touristSummary").value("진주의 대표 역사 관광지입니다."))
                .andExpect(jsonPath(
                        "$.places[0].touristCategories",
                        containsInAnyOrder("EXHIBITION", "OTHER")
                ));

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "jinju castle")
                        .param("latitude", "35.1894")
                        .param("longitude", "128.0789")
                        .param("radiusKm", "1.0")
                        .param("sort", "NEAREST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].englishName").value("Jinju Castle"))
                .andExpect(jsonPath(
                        "$.places[0].touristCategories",
                        containsInAnyOrder("EXHIBITION", "OTHER")
                ));

        mockMvc.perform(get("/places/{id}", touristPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.englishName").value("Jinju Castle"))
                .andExpect(jsonPath("$.touristSummary").value("진주의 대표 역사 관광지입니다."))
                .andExpect(jsonPath("$.touristCategories", containsInAnyOrder("EXHIBITION", "OTHER")));

        mockMvc.perform(get("/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "Jinju Castle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(touristPlace.getId()))
                .andExpect(jsonPath("$.places[0].englishName").value("Jinju Castle"));
    }

    @Test
    void placeDetailExposesRegularHoursAndOperatingExceptions() throws Exception {
        String accessToken = signupAndLogin("readerOperatingSchedule");
        MapPlace mapPlace = createMapPlace("영업시간 장소", "경상남도 진주시 영업로 3");
        mapPlace.replaceOperatingSchedule(
                Set.of(PlaceRegularOperatingHour.of(
                        DayOfWeek.MONDAY,
                        LocalTime.of(9, 0),
                        LocalTime.of(18, 0)
                )),
                List.of(
                        PlaceOperatingException.closed(mapPlace, LocalDate.of(2026, 8, 15)),
                        PlaceOperatingException.customHours(
                                mapPlace,
                                LocalDate.of(2026, 8, 16),
                                Set.of(PlaceOperatingTimeRange.of(LocalTime.of(10, 0), LocalTime.of(16, 0)))
                        )
                )
        );
        mapPlace = mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/places/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularHours[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.regularHours[0].opensAt").value("09:00:00"))
                .andExpect(jsonPath("$.operatingExceptions.length()").value(2))
                .andExpect(jsonPath("$.operatingExceptions[0].date").value("2026-08-15"))
                .andExpect(jsonPath("$.operatingExceptions[0].closed").value(true))
                .andExpect(jsonPath("$.operatingExceptions[1].hours[0].closesAt").value("16:00:00"));
    }

    @Test
    void autocompleteRanksNormalizedJibunAddressAboveCategoryMatch() throws Exception {
        String accessToken = signupAndLogin("addressRank" + Long.toUnsignedString(System.nanoTime()));
        MapPlace jibunAddressPlace = createMapPlace(
                "지번 주소 장소",
                "대표 주소",
                "관광",
                35.1894,
                128.0789
        );
        jibunAddressPlace.updateGeocoding(
                "대표 주소",
                null,
                "경상남도 진주시 본성동 500-8",
                "52692",
                jibunAddressPlace.getLatitude(),
                jibunAddressPlace.getLongitude(),
                jibunAddressPlace.getLocation(),
                GeocodingSource.USER_PIN
        );
        mapPlaceRepository.save(jibunAddressPlace);
        createMapPlace("카테고리 후보", "다른 주소", "본성동", 35.1895, 128.0790);

        mockMvc.perform(get("/places/autocomplete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "본성동"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].id").value(jibunAddressPlace.getId()));
    }

    @Test
    void createCoordinatesAllowsMissingKakaoPlaceId() throws Exception {
        String accessToken = signupAndLogin("placeUploaderNoKakao01");

        MvcResult coordinateResult = mockMvc.perform(post("/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseLatitude", 35.1811,
                                "baseLongitude", 128.1081
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        assertNotNull(objectMapper.readTree(coordinateResult.getResponse().getContentAsString()).get("coordinateToken").textValue());
        assertNull(objectMapper.readTree(coordinateResult.getResponse().getContentAsString()).get("kakaoPlaceId").textValue());
    }

    @Test
    void uploadPlaceStoresPlaceWithoutKakaoPlaceId() throws Exception {
        String accessToken = signupAndLogin("placeUploaderNoKakao02");
        String coordinateToken = createCoordinateToken(accessToken, null, 35.1812, 128.1082);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "핀 좌표 장소",
                                "address", "경상남도 진주시 핀좌표로 2",
                                "category", "풍경",
                                "imageUrl", "https://example.com/images/pin-place.jpg",
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("핀 좌표 장소"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 핀좌표로 2"));

        MapPlace saved = mapPlaceRepository.findAll().stream()
                .filter(place -> "핀 좌표 장소".equals(place.getName()))
                .findFirst()
                .orElseThrow();
        assertNull(saved.getKakaoPlaceId());
        assertEquals("풍경", saved.getCategory());
        assertEquals("https://example.com/images/pin-place.jpg", saved.getImageUrl());
        assertNull(saved.getEnglishName());
        assertNull(saved.getTouristSummary());
        assertEquals(Set.of(), saved.currentTouristCategories());
        mapPlaceRepository.flush();
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM map_place_tourist_guard WHERE map_place_id = ?",
                Integer.class,
                saved.getId()
        ));
    }

    @Test
    void uploadPlaceNormalizesCategoryAlias() throws Exception {
        String accessToken = signupAndLogin("placeUploaderCategory01");
        String coordinateToken = createCoordinateToken(accessToken, "27414319", 35.1804, 128.1081);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kakaoPlaceId", "27414319",
                                "name", "카테고리 표준화 장소",
                                "address", "경상남도 진주시 표준화로 1",
                                "category", "  커피  ",
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isCreated());

        MapPlace saved = mapPlaceRepository.findByKakaoPlaceId("27414319").orElseThrow();
        assertEquals("카페", saved.getCategory());
    }

    @Test
    void listPlacesSearchesByStandardizedCategoryAlias() throws Exception {
        String accessToken = signupAndLogin("readerSearchCategory01");
        MapPlace matchingPlace = createMapPlace(
                "표준 카페",
                "경상남도 진주시 표준로 10",
                "카페",
                35.1894,
                128.0789
        );
        createMapPlace("표준 식당", "경상남도 진주시 표준로 11", "식당", 35.1801, 128.1078);

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("category", " coffee "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(matchingPlace.getId()))
                .andExpect(jsonPath("$.places[0].category").value("카페"));
    }

    @Test
    void uploadPlaceStoresNullImageUrlWhenBlank() throws Exception {
        String accessToken = signupAndLogin("placeUploader02");
        String coordinateToken = createCoordinateToken(accessToken, "27414317", 35.1802, 128.1079);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kakaoPlaceId", "27414317",
                                "name", "이미지 없는 장소",
                                "address", "경상남도 진주시 이미지로 2",
                                "category", "식당",
                                "imageUrl", "   ",
                                "englishName", "   ",
                                "touristSummary", "   ",
                                "touristCategories", List.of(),
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("이미지 없는 장소"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 이미지로 2"))
                .andExpect(jsonPath("$.englishName").isEmpty())
                .andExpect(jsonPath("$.touristSummary").isEmpty())
                .andExpect(jsonPath("$.touristCategories.length()").value(0));

        MapPlace saved = mapPlaceRepository.findByKakaoPlaceId("27414317").orElseThrow();
        assertEquals("식당", saved.getCategory());
        assertNull(saved.getImageUrl());
        assertNull(saved.getEnglishName());
        assertNull(saved.getTouristSummary());
        assertEquals(Set.of(), saved.currentTouristCategories());
    }

    @Test
    void uploadPlaceReturnsBadRequestWhenCategoryIsBlank() throws Exception {
        String accessToken = signupAndLogin("placeUploader03");
        String coordinateToken = createCoordinateToken(accessToken, "27414318", 35.1803, 128.1080);

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kakaoPlaceId", "27414318",
                                "name", "카테고리 없는 장소",
                                "address", "경상남도 진주시 이미지로 3",
                                "category", "   ",
                                "imageUrl", "https://example.com/images/place-upload-blank-category.jpg",
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").value("카테고리는 필수입니다."));
    }

    @Test
    void getPlaceReturnsNotFoundWhenPlaceDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("reader03");

        mockMvc.perform(get("/places/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void createExplorationMediaReturnsCreatedMediaContract() throws Exception {
        String accessToken = signupAndLogin("placeMediaOwner01");
        Long ownerId = userRepository.findByUsername("placeMediaOwner01").orElseThrow().getId();
        MapPlace place = createMapPlace("탐색 미디어 장소", "경상남도 진주시 미디어로 1", ownerId);

        mockMvc.perform(post("/places/{id}/media/exploration", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imageUrl", "https://cdn.pingdom.test/exploration.jpg",
                                "s3Key", "places/exploration.jpg",
                                "thumbnailUrl", "https://cdn.pingdom.test/exploration-thumb.jpg",
                                "thumbnailS3Key", "places/exploration-thumb.jpg"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.purpose").value("EXPLORATION"))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.pingdom.test/exploration.jpg"))
                .andExpect(jsonPath("$.sourceMapImageId").isEmpty())
                .andExpect(jsonPath("$.displayOrder").value(0));
    }

    @Test
    void placeMediaEndpointsSeparateExplorationAndVerificationMedia() throws Exception {
        String accessToken = signupAndLogin("placeMediaOwner02");
        Long ownerId = userRepository.findByUsername("placeMediaOwner02").orElseThrow().getId();
        MapPlace place = createMapPlace("미디어 분리 장소", "경상남도 진주시 분리로 1", ownerId);
        PlaceMedia explorationMedia = placeMediaRepository.save(PlaceMedia.exploration(
                place,
                "https://cdn.pingdom.test/exploration.jpg",
                "places/exploration.jpg",
                null,
                null,
                1,
                LocalDateTime.of(2026, 7, 21, 10, 0)
        ));
        MapImage mapImage = createMapImage(place, 0L, "검증용 사진");
        PlaceMedia verificationMedia = placeMediaRepository.save(PlaceMedia.verification(
                place,
                "https://cdn.pingdom.test/verification.jpg",
                "places/verification.jpg",
                "https://cdn.pingdom.test/verification-thumb.jpg",
                "places/verification-thumb.jpg",
                mapImage.getId(),
                LocalDateTime.of(2026, 7, 21, 10, 5)
        ));

        mockMvc.perform(get("/places/{id}/media/exploration", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(explorationMedia.getId()))
                .andExpect(jsonPath("$.media[0].purpose").value("EXPLORATION"))
                .andExpect(jsonPath("$.media[0].sourceMapImageId").isEmpty());

        mockMvc.perform(get("/places/{id}/media/verification", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(verificationMedia.getId()))
                .andExpect(jsonPath("$.media[0].purpose").value("VERIFICATION"))
                .andExpect(jsonPath("$.media[0].sourceMapImageId").value(mapImage.getId()));
    }

    @Test
    void createExplorationMediaRejectsBlankImageUrl() throws Exception {
        String accessToken = signupAndLogin("placeMediaOwner03");
        Long ownerId = userRepository.findByUsername("placeMediaOwner03").orElseThrow().getId();
        MapPlace place = createMapPlace("미디어 검증 장소", "경상남도 진주시 검증로 1", ownerId);

        mockMvc.perform(post("/places/{id}/media/exploration", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.imageUrl").value("imageUrl은 필수입니다."));
    }

    @Test
    void getExplorationMediaRejectsHiddenDiscoveryPlace() throws Exception {
        String accessToken = signupAndLogin("placeMediaReader01");
        Long ownerId = userRepository.findByUsername("placeMediaReader01").orElseThrow().getId();
        MapPlace hiddenPlace = createMapPlace("숨김 미디어 장소", "경상남도 진주시 숨김미디어로 1", ownerId);
        hiddenPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(hiddenPlace);
        placeMediaRepository.save(PlaceMedia.exploration(
                hiddenPlace,
                "https://cdn.pingdom.test/hidden.jpg",
                null,
                null,
                null,
                0,
                LocalDateTime.of(2026, 7, 21, 11, 0)
        ));

        mockMvc.perform(get("/places/{id}/media/exploration", hiddenPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void getVerificationMediaRequiresPlaceOwner() throws Exception {
        String ownerToken = signupAndLogin("placeMediaOwner04");
        String otherToken = signupAndLogin("placeMediaOther04");
        Long ownerId = userRepository.findByUsername("placeMediaOwner04").orElseThrow().getId();
        MapPlace place = createMapPlace("검증 권한 장소", "경상남도 진주시 권한로 1", ownerId);

        mockMvc.perform(get("/places/{id}/media/verification", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OTHERS_PLACE_MEDIA_NOT_MANAGED"));

        mockMvc.perform(get("/places/{id}/media/verification", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media.length()").value(0));
    }

    @Test
    void deleteExplorationMediaDoesNotDeleteVerificationMedia() throws Exception {
        String accessToken = signupAndLogin("placeMediaOwner05");
        Long ownerId = userRepository.findByUsername("placeMediaOwner05").orElseThrow().getId();
        MapPlace place = createMapPlace("미디어 삭제 장소", "경상남도 진주시 삭제로 1", ownerId);
        PlaceMedia explorationMedia = placeMediaRepository.save(PlaceMedia.exploration(
                place,
                "https://cdn.pingdom.test/delete-exploration.jpg",
                null,
                null,
                null,
                0,
                LocalDateTime.of(2026, 7, 21, 12, 0)
        ));
        MapImage mapImage = createMapImage(place, 0L, "삭제 검증 사진");
        PlaceMedia verificationMedia = placeMediaRepository.save(PlaceMedia.verification(
                place,
                "https://cdn.pingdom.test/delete-verification.jpg",
                "places/delete-verification.jpg",
                null,
                null,
                mapImage.getId(),
                LocalDateTime.of(2026, 7, 21, 12, 5)
        ));

        mockMvc.perform(delete("/places/{id}/media/exploration/{mediaId}", place.getId(), explorationMedia.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("장소 탐색용 미디어를 삭제했습니다."));

        assertFalse(placeMediaRepository.existsById(explorationMedia.getId()));
        assertNotNull(placeMediaRepository.findById(verificationMedia.getId()).orElseThrow());
    }

    @Test
    void recommendPlacesAllowsAnonymousUserWhenPrincipalIsNull() {
        MapPlace mapPlace = createMapPlace("비로그인 추천 장소", "경상남도 진주시 익명로 1", 35.1801, 128.1078, 1L);
        createMapImage(mapPlace, 0L, "비로그인 추천 사진");

        var response = placeController.recommendPlaces(35.1801, 128.1078, 1, 5.0, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().recommendedCount());
        assertNotNull(response.getBody().recommendationRequestId());
        assertEquals("비로그인 추천 장소", response.getBody().places().get(0).name());
    }

    @Test
    void recommendPlacesReturnsPersonalizedNearbyPlaces() throws Exception {
        String accessToken = signupAndLogin("reader04");
        User reader = userRepository.findByUsername("reader04").orElseThrow();

        MapPlace bookmarkedPlace = createMapPlace("북마크 기준 장소", "경상남도 진주시 강남로 1", 35.1800, 128.1070, 1L);
        MapPlace recommendedPlace = createMapPlace("추천 장소", "경상남도 진주시 강남로 2", 35.1804, 128.1075, 3L);
        MapPlace fallbackPlace = createMapPlace("일반 후보 장소", "경상남도 진주시 강남로 3", 35.1840, 128.1110, 2L);

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(reader.getId())
                .placeId(bookmarkedPlace.getId())
                .build());

        createMapImage(recommendedPlace, 12L, "추천 사진 1");
        createMapImage(recommendedPlace, 9L, "추천 사진 2");
        createMapImage(recommendedPlace, 8L, "추천 사진 3");
        createMapImage(fallbackPlace, 2L, "일반 사진 1");
        createMapImage(fallbackPlace, 1L, "일반 사진 2");

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1802")
                        .param("longitude", "128.1072")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v1"))
                .andExpect(jsonPath("$.recommendationRequestId").isNotEmpty())
                .andExpect(jsonPath("$.recommendedCount").value(2))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].name").value("추천 장소"))
                .andExpect(jsonPath("$.places[0].reason").value("저장한 장소와 가까운 추천 장소입니다."))
                .andExpect(jsonPath("$.places[0].reasonCode").value("PERSONAL_SIGNAL"))
                .andExpect(jsonPath("$.limitReasons").isArray());
    }

    @Test
    void recommendPlacesUsesBookmarkSimilarityForPersonalRanking() throws Exception {
        String accessToken = signupAndLogin("reader06");
        User reader = userRepository.findByUsername("reader06").orElseThrow();

        MapPlace seedPlace = createMapPlace("기준 장소", "경상남도 진주시 초전동 1", 35.1800, 128.1070, 1L);
        MapPlace similarPlace = createMapPlace("유사 장소", "경상남도 진주시 초전동 2", 35.1830, 128.1100, 1L);
        MapPlace nearbyPlace = createMapPlace("가까운 일반 장소", "경상남도 진주시 초전동 3", 35.1804, 128.1074, 1L);

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(reader.getId())
                .placeId(seedPlace.getId())
                .build());

        User userA = createUser("similarityUserA");
        User userB = createUser("similarityUserB");

        createBookmark(userA.getId(), seedPlace.getId());
        createBookmark(userA.getId(), similarPlace.getId());
        createBookmark(userB.getId(), seedPlace.getId());
        createBookmark(userB.getId(), similarPlace.getId());

        createMapImage(similarPlace, 1L, "유사 장소 사진");
        createMapImage(nearbyPlace, 1L, "가까운 장소 사진");

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1802")
                        .param("longitude", "128.1072")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("유사 장소"))
                .andExpect(jsonPath("$.places[0].reason").value("저장한 장소와 가까운 추천 장소입니다."));
    }

    @Test
    void recommendPlacesIncludesPersonalCandidatesOutsideCurrentGeoArea() throws Exception {
        String accessToken = signupAndLogin("reader19");
        User reader = userRepository.findByUsername("reader19").orElseThrow();

        MapPlace seedPlace = createMapPlace("개인화 기준 장소", "경상남도 진주시 개인화로 1", 35.1800, 128.1070, 1L);
        MapPlace personalCandidate = createMapPlace("개인화 확장 장소", "경상남도 진주시 개인화로 2", 35.1810, 128.1080, 3L);

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(reader.getId())
                .placeId(seedPlace.getId())
                .build());

        createMapImage(personalCandidate, 12L, "개인화 사진 1");
        createMapImage(personalCandidate, 9L, "개인화 사진 2");
        createMapImage(personalCandidate, 7L, "개인화 사진 3");

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("limit", "1")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedCount").value(1))
                .andExpect(jsonPath("$.places[0].name").value("개인화 확장 장소"));
    }

    @Test
    void recommendPlacesFallsBackToPopularNearbyPlacesWhenUserHasNoSignals() throws Exception {
        String accessToken = signupAndLogin("reader05");

        MapPlace popularPlace = createMapPlace("인기 장소", "경상남도 진주시 남강로 10", 35.1803, 128.1079, 4L);
        MapPlace normalPlace = createMapPlace("일반 장소", "경상남도 진주시 남강로 11", 35.1816, 128.1082, 1L);

        createMapImage(popularPlace, 20L, "인기 사진 1");
        createMapImage(popularPlace, 15L, "인기 사진 2");
        createMapImage(popularPlace, 10L, "인기 사진 3");
        createMapImage(popularPlace, 6L, "인기 사진 4");
        createMapImage(normalPlace, 0L, "일반 사진 1");

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedCount").value(2))
                .andExpect(jsonPath("$.places[0].name").value("인기 장소"))
                .andExpect(jsonPath("$.places[0].reason", containsString("현재 위치 주변")));
    }

    @Test
    void recommendPlacesIncludesTrendCandidatesWhenNoGeoCandidatesExist() throws Exception {
        String accessToken = signupAndLogin("reader20");
        MapPlace trendPlace = createMapPlace("트렌드 후보 장소", "경상남도 진주시 트렌드로 1", 35.1803, 128.1079, 2L);

        LocalDateTime recent = LocalDateTime.now();
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(trendPlace.getId())
                .photoCount(2L)
                .bookmarkCount(0L)
                .totalLikeCount(15L)
                .clickCount(3L)
                .exposureCount(5L)
                .latestPostCreatedAt(recent)
                .updatedAt(recent)
                .build());

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "37.5665")
                        .param("longitude", "126.9780")
                        .param("limit", "1")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedCount").value(1))
                .andExpect(jsonPath("$.places[0].name").value("트렌드 후보 장소"));
    }

    @Test
    void recommendPlacesRecordsExposureLogs() throws Exception {
        String accessToken = signupAndLogin("reader10");

        MapPlace firstPlace = createMapPlace("노출 장소 A", "경상남도 진주시 본성동 1", 35.1802, 128.1078, 1L);
        MapPlace secondPlace = createMapPlace("노출 장소 B", "경상남도 진주시 본성동 2", 35.1804, 128.1080, 1L);

        createMapImage(firstPlace, 4L, "노출 사진 A");
        createMapImage(secondPlace, 3L, "노출 사진 B");

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedCount").value(2));

        TestTransaction.flagForCommit();
        TestTransaction.end();

        List<PlaceRecommendationExposure> exposures = waitForValue(
                () -> placeRecommendationExposureRepository.findAll().stream()
                        .sorted(Comparator.comparing(PlaceRecommendationExposure::getRanking))
                        .toList(),
                loaded -> loaded.size() == 2
        );

        assertEquals(2, exposures.size());
        assertEquals(1, exposures.get(0).getRanking());
        assertEquals(2, exposures.get(1).getRanking());
        assertNotNull(exposures.get(0).getCreatedAt());
        assertNotNull(exposures.get(0).getRequestId());
        assertEquals(35.1801d, exposures.get(0).getRequestLatitude());
        assertEquals(128.1078d, exposures.get(0).getRequestLongitude());

        PlaceRecommendationSnapshot firstSnapshot = waitForValue(
                () -> placeRecommendationSnapshotRepository.findById(firstPlace.getId()).orElseThrow(),
                snapshot -> snapshot.getExposureCount() == 1L
        );
        PlaceRecommendationSnapshot secondSnapshot = waitForValue(
                () -> placeRecommendationSnapshotRepository.findById(secondPlace.getId()).orElseThrow(),
                snapshot -> snapshot.getExposureCount() == 1L
        );
        assertEquals(1L, firstSnapshot.getExposureCount());
        assertEquals(1L, secondSnapshot.getExposureCount());
        cleanupCommittedRecommendationTestData();
    }

    @Test
    void recommendPlacesAppliesExplorationBonusForLowExposurePlace() throws Exception {
        String accessToken = signupAndLogin("reader11");

        MapPlace lowExposurePlace = createMapPlace("저노출 장소", "경상남도 진주시 신안동 1", 35.1803, 128.1079, 1L);
        MapPlace highExposurePlace = createMapPlace("고노출 장소", "경상남도 진주시 신안동 2", 35.1803, 128.1079, 1L);

        createMapImage(lowExposurePlace, 5L, "저노출 사진");
        createMapImage(highExposurePlace, 5L, "고노출 사진");
        createExposureLogs(highExposurePlace.getId(), 30, 35.1801, 128.1078);

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "1")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("저노출 장소"));
    }

    @Test
    void recordRecommendationClickStoresLogAndIncreasesSnapshotCount() throws Exception {
        String accessToken = signupAndLogin("reader12");
        MapPlace clickedPlace = createMapPlace("클릭 장소", "경상남도 진주시 클릭로 1", 35.1803, 128.1079, 1L);
        createMapImage(clickedPlace, 2L, "클릭 사진");
        String requestId = "click-count-request";

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", clickedPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", requestId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(clickedPlace.getId()))
                .andExpect(jsonPath("$.message").value("추천 장소 클릭을 기록했습니다."));

        List<PlaceRecommendationClick> clicks = placeRecommendationClickRepository.findAll();
        assertEquals(1, clicks.size());
        assertEquals(clickedPlace.getId(), clicks.get(0).getPlaceId());
        assertNotNull(clicks.get(0).getCreatedAt());
        assertEquals(requestId, clicks.get(0).getRequestId());

        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(clickedPlace.getId())
                .orElseThrow();
        assertEquals(1L, snapshot.getClickCount());
        assertEquals(0L, snapshot.getExposureCount());
    }

    @Test
    void recordRecommendationClickRejectsHiddenDiscoveryPlace() throws Exception {
        String accessToken = signupAndLogin("readerHiddenRecommendationClick" + Long.toUnsignedString(System.nanoTime()));
        MapPlace hiddenPlace = createMapPlace("숨김 추천 클릭 장소", "경상남도 진주시 숨김추천로 1", 35.1803, 128.1079, 1L);
        hiddenPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(hiddenPlace);

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", hiddenPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "hidden-discovery-click-request"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));

        long hiddenPlaceClickCount = placeRecommendationClickRepository.findAll().stream()
                .filter(click -> hiddenPlace.getId().equals(click.getPlaceId()))
                .count();
        assertEquals(0L, hiddenPlaceClickCount);
        assertFalse(placeRecommendationSnapshotRepository.existsById(hiddenPlace.getId()));
    }

    @Test
    void createBookmarkRecordsRecommendationBookmarkConversion() throws Exception {
        String accessToken = signupAndLogin("reader15");
        MapPlace mapPlace = createMapPlace("북마크 전환 장소", "경상남도 진주시 전환로 1", 35.1803, 128.1079, 1L);

        MvcResult recommendationResult = mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1803")
                        .param("longitude", "128.1079")
                        .param("limit", "1")
                        .param("radiusKm", "5.0")
                        .param("recommendationVersion", "place-rec-v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v2"))
                .andExpect(jsonPath("$.recommendationRequestId").isNotEmpty())
                .andReturn();

        String requestId = objectMapper.readTree(recommendationResult.getResponse().getContentAsString())
                .get("recommendationRequestId")
                .asText();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        waitForValue(
                placeRecommendationExposureRepository::findAll,
                loaded -> loaded.size() == 1
        );

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", mapPlace.getId(),
                                "recommendationVersion", "place-rec-v2",
                                "requestId", requestId
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("placeId", mapPlace.getId()))))
                .andExpect(status().isCreated());

        List<PlaceRecommendationConversion> conversions = placeRecommendationConversionRepository.findAll();
        assertEquals(1, conversions.size());
        assertEquals(mapPlace.getId(), conversions.get(0).getPlaceId());
        assertEquals(PlaceRecommendationConversionType.BOOKMARK, conversions.get(0).getConversionType());
        assertEquals("place-rec-v2", conversions.get(0).getRecommendationVersion());
        assertNotNull(conversions.get(0).getCreatedAt());
        assertNotNull(conversions.get(0).getPlaceRecommendationClickId());
        List<PlaceRecommendationFeatureLog> attributedFeatureLogs = placeRecommendationFeatureLogRepository
                .findByRequestIdAndUserIdOrderByRankingAsc(requestId, conversions.get(0).getUserId());
        assertEquals(1, attributedFeatureLogs.size());
        assertEquals(
                attributedFeatureLogs.get(0).getId(),
                conversions.get(0).getPlaceRecommendationFeatureLogId()
        );

        List<PlaceRecommendationExposure> exposures = waitForValue(
                placeRecommendationExposureRepository::findAll,
                loaded -> loaded.size() == 1
        );
        assertEquals(1, exposures.size());
        assertEquals("place-rec-v2", exposures.get(0).getRecommendationVersion());

        List<PlaceRecommendationClick> clicks = placeRecommendationClickRepository.findAll();
        assertEquals(1, clicks.size());
        assertEquals("place-rec-v2", clicks.get(0).getRecommendationVersion());
        cleanupCommittedRecommendationTestData();
    }

    @Test
    void recommendPlacesSupportsExplicitExperimentalVersionAndLogsFeatures() throws Exception {
        String accessToken = signupAndLogin("reader18");
        MapPlace freshPlace = createMapPlace("실험 신선 후보", "경상남도 진주시 실험로 1", 35.1803, 128.1079, 1L);
        createMapImage(freshPlace, 5L, "실험 후보 사진");

        MvcResult result = mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1803")
                        .param("longitude", "128.1079")
                        .param("limit", "1")
                        .param("radiusKm", "5.0")
                        .param("recommendationVersion", "place-rec-v2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v2"))
                .andExpect(jsonPath("$.recommendationRequestId").isNotEmpty())
                .andReturn();

        TestTransaction.flagForCommit();
        TestTransaction.end();

        String requestId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("recommendationRequestId")
                .asText();

        List<PlaceRecommendationFeatureLog> featureLogs =
                placeRecommendationFeatureLogRepository.findByRequestIdOrderByRankingAsc(requestId);
        assertEquals(1, featureLogs.size());
        assertEquals("place-rec-v2", featureLogs.get(0).getRecommendationVersion());
        assertEquals(requestId, featureLogs.get(0).getRequestId());

        List<PlaceRecommendationExposure> exposures = waitForValue(
                placeRecommendationExposureRepository::findAll,
                loaded -> loaded.size() == 1
        );
        assertEquals(1, exposures.size());
        assertEquals("place-rec-v2", exposures.get(0).getRecommendationVersion());
        assertEquals(requestId, exposures.get(0).getRequestId());
        cleanupCommittedRecommendationTestData();
    }

    @Test
    void recordRecommendationClickStoresRequestIdWhenProvided() throws Exception {
        String accessToken = signupAndLogin("reader19");
        MapPlace clickedPlace = createMapPlace("요청 추적 클릭 장소", "경상남도 진주시 추적으로 1", 35.1803, 128.1079, 1L);
        createMapImage(clickedPlace, 1L, "요청 추적 사진");

        MvcResult recommendationResult = mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1803")
                        .param("longitude", "128.1079")
                        .param("limit", "1")
                        .param("radiusKm", "5.0")
                        .param("recommendationVersion", "place-rec-v2"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = objectMapper.readTree(recommendationResult.getResponse().getContentAsString())
                .get("recommendationRequestId")
                .asText();

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", clickedPlace.getId(),
                                "recommendationVersion", "place-rec-v2",
                                "requestId", requestId
                        ))))
                .andExpect(status().isCreated());

        List<PlaceRecommendationClick> clicks = placeRecommendationClickRepository.findAll();
        assertEquals(1, clicks.size());
        assertEquals("place-rec-v2", clicks.get(0).getRecommendationVersion());
        assertEquals(requestId, clicks.get(0).getRequestId());
    }

    @Test
    void getRecommendationExplanationReturnsOnlyOwnedLogs() throws Exception {
        String ownerToken = signupAndLogin("reader20");
        String otherToken = signupAndLogin("reader21");

        Long ownerId = userRepository.findByUsername("reader20").orElseThrow().getId();
        Long otherId = userRepository.findByUsername("reader21").orElseThrow().getId();
        MapPlace ownerPlace = createMapPlace("설명 조회 장소", "경상남도 진주시 설명로 1", "카페", 35.1803, 128.1079);
        MapPlace otherPlace = createMapPlace("다른 사용자 장소", "경상남도 진주시 설명로 2", "카페", 35.1804, 128.1080);

        placeRecommendationFeatureLogRepository.save(PlaceRecommendationFeatureLog.builder()
                .requestId("req-owner-1")
                .userId(ownerId)
                .placeId(ownerPlace.getId())
                .recommendationVersion("place-rec-v2")
                .recommendationStage(RecommendationStage.EXPERIMENTAL)
                .candidateSource(PlaceRecommendationCandidateSource.PERSONAL)
                .ranking(1)
                .distanceMeters(120)
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
        placeRecommendationFeatureLogRepository.save(PlaceRecommendationFeatureLog.builder()
                .requestId("req-owner-1")
                .userId(otherId)
                .placeId(otherPlace.getId())
                .recommendationVersion("place-rec-v2")
                .recommendationStage(RecommendationStage.EXPERIMENTAL)
                .candidateSource(PlaceRecommendationCandidateSource.POPULAR)
                .ranking(1)
                .distanceMeters(180)
                .geoScore(0.5d)
                .personalScore(0.4d)
                .qualityScore(0.3d)
                .engagementScore(0.2d)
                .conversionScore(0.1d)
                .explorationScore(0.2d)
                .freshnessScore(0.1d)
                .finalScore(0.55d)
                .build());

        mockMvc.perform(get("/places/recommendations/{requestId}/explanation", "req-owner-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-owner-1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(ownerPlace.getId()))
                .andExpect(jsonPath("$.items[0].placeName").value("설명 조회 장소"))
                .andExpect(jsonPath("$.items[0].source").value("PERSONAL"))
                .andExpect(jsonPath("$.items[0].ranking").value(1))
                .andExpect(jsonPath("$.items[0].benefitScore").value(0.05d))
                .andExpect(jsonPath("$.items[0].availabilityScore").value(0.04d))
                .andExpect(jsonPath("$.items[0].finalScore").value(0.95d));

        mockMvc.perform(get("/places/recommendations/{requestId}/explanation", "req-owner-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(otherPlace.getId()));
    }

    @Test
    void getRecommendationExplanationReturnsNotFoundWhenMissing() throws Exception {
        String accessToken = signupAndLogin("reader22");

        mockMvc.perform(get("/places/recommendations/{requestId}/explanation", "missing-request-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EXPLANATION_NOT_FOUND"));
    }

    @Test
    void getRecommendationExplanationReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/places/recommendations/{requestId}/explanation", "missing-request-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyRecommendationClickReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/place/recommendations/click")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", 1L,
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "legacy-unauthorized-test"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void legacyRecommendationExplanationReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/place/recommendations/{requestId}/explanation", "legacy-unauthorized-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void legacyPlaceCoordinateCreateReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/map/places/coordinates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseLatitude", 35.1814,
                                "baseLongitude", 128.1084
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void legacyPlaceUploadReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/map/places/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "무인증 레거시 장소",
                                "address", "경상남도 진주시 테스트로 1",
                                "category", "풍경",
                                "imageUrl", "https://example.com/images/legacy-place.jpg",
                                "coordinateToken", "invalid-token"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void legacyPlaceDeleteReturnsUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(delete("/map/places/{id}/delete", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void likeRecordsRecommendationLikeConversion() throws Exception {
        String accessToken = signupAndLogin("reader16");
        MapPlace mapPlace = createMapPlace("좋아요 전환 장소", "경상남도 진주시 전환로 2", 35.1803, 128.1079, 1L);
        MapImage mapImage = createMapImage(mapPlace, 0L, "좋아요 전환 사진");

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", mapPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "like-conversion-request"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("mapImageId", mapImage.getId()))))
                .andExpect(status().isOk());

        List<PlaceRecommendationConversion> conversions = placeRecommendationConversionRepository.findAll();
        assertEquals(1, conversions.size());
        assertEquals(mapPlace.getId(), conversions.get(0).getPlaceId());
        assertEquals(PlaceRecommendationConversionType.LIKE, conversions.get(0).getConversionType());
        assertNotNull(conversions.get(0).getPlaceRecommendationClickId());
    }

    @Test
    void likeConversionIsRecordedOnlyOncePerUserAndPlace() throws Exception {
        String accessToken = signupAndLogin("reader17");
        MapPlace mapPlace = createMapPlace("중복 전환 장소", "경상남도 진주시 전환로 3", 35.1803, 128.1079, 2L);
        MapImage firstImage = createMapImage(mapPlace, 0L, "중복 전환 사진 1");
        MapImage secondImage = createMapImage(mapPlace, 0L, "중복 전환 사진 2");

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", mapPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "like-conversion-once-request"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("mapImageId", firstImage.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/map/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("mapImageId", secondImage.getId()))))
                .andExpect(status().isOk());

        List<PlaceRecommendationConversion> conversions = placeRecommendationConversionRepository.findAll();
        assertEquals(1, conversions.size());
        assertEquals(PlaceRecommendationConversionType.LIKE, conversions.get(0).getConversionType());
        assertEquals(mapPlace.getId(), conversions.get(0).getPlaceId());
    }

    @Test
    void recommendPlacesAppliesCtrScoreToWellClickedPlace() throws Exception {
        String accessToken = signupAndLogin("reader13");

        MapPlace wellClickedPlace = createMapPlace("검증된 클릭 반응 장소", "경상남도 진주시 반응로 1", 35.1803, 128.1079, 1L);
        MapPlace lowClickedPlace = createMapPlace("낮은 클릭 반응 장소", "경상남도 진주시 반응로 2", 35.1803, 128.1079, 1L);

        LocalDateTime now = LocalDateTime.now().minusDays(30);
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(wellClickedPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(6L)
                .exposureCount(20L)
                .latestPostCreatedAt(now)
                .updatedAt(now)
                .build());
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(lowClickedPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(0L)
                .exposureCount(20L)
                .latestPostCreatedAt(now)
                .updatedAt(now)
                .build());

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("검증된 클릭 반응 장소"))
                .andExpect(jsonPath("$.places[0].reason").value("현재 위치 주변에서 추천 클릭 반응이 좋은 장소입니다."));
    }

    @Test
    void recommendPlacesProtectsAgainstSingleLuckyClickSample() throws Exception {
        String accessToken = signupAndLogin("reader14");

        MapPlace luckyClickPlace = createMapPlace("우연 클릭 장소", "경상남도 진주시 반응로 3", 35.1803, 128.1079, 1L);
        MapPlace provenClickPlace = createMapPlace("검증된 반응 장소", "경상남도 진주시 반응로 4", 35.1803, 128.1079, 1L);

        LocalDateTime now = LocalDateTime.now().minusDays(30);
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(luckyClickPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(1L)
                .exposureCount(1L)
                .latestPostCreatedAt(now)
                .updatedAt(now)
                .build());
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(provenClickPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(6L)
                .exposureCount(20L)
                .latestPostCreatedAt(now)
                .updatedAt(now)
                .build());

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("검증된 반응 장소"));
    }

    @Test
    void recommendPlacesPrefersPlaceWithBetterConversionQuality() throws Exception {
        String accessToken = signupAndLogin("reader18");

        MapPlace highConversionPlace = createMapPlace("전환 우수 장소", "경상남도 진주시 반응로 5", 35.1803, 128.1079, 1L);
        MapPlace lowConversionPlace = createMapPlace("전환 낮은 장소", "경상남도 진주시 반응로 6", 35.1803, 128.1079, 1L);

        LocalDateTime now = LocalDateTime.now().minusDays(30);
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(highConversionPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(4L)
                .bookmarkConversionCount(2L)
                .likeConversionCount(1L)
                .exposureCount(20L)
                .latestPostCreatedAt(now)
                .updatedAt(now)
                .build());
        placeRecommendationSnapshotRepository.save(PlaceRecommendationSnapshot.builder()
                .placeId(lowConversionPlace.getId())
                .photoCount(1L)
                .bookmarkCount(0L)
                .totalLikeCount(0L)
                .clickCount(4L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(20L)
                .latestPostCreatedAt(now)
                .updatedAt(now)
                .build());

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("전환 우수 장소"))
                .andExpect(jsonPath("$.places[0].reason").value("현재 위치 주변에서 저장 전환 반응이 좋은 장소입니다."));
    }

    @Test
    void createAndRemoveBookmarkRefreshRecommendationSnapshot() throws Exception {
        String accessToken = signupAndLogin("reader08");
        MapPlace mapPlace = createMapPlace("북마크 검증 장소", "경상남도 진주시 칠암동 1");

        mockMvc.perform(post("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("placeId", mapPlace.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()));

        PlaceRecommendationSnapshot createdSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(1L, createdSnapshot.getBookmarkCount());
        assertEquals(0L, createdSnapshot.getTotalLikeCount());

        mockMvc.perform(delete("/users/me/bookmarks/{placeId}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()));

        PlaceRecommendationSnapshot removedSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(0L, removedSnapshot.getBookmarkCount());
    }

    @Test
    void createBookmarkRejectsHiddenDiscoveryPlace() throws Exception {
        String username = "readerHiddenBookmark" + Long.toUnsignedString(System.nanoTime());
        String accessToken = signupAndLogin(username);
        User user = userRepository.findByUsername(username).orElseThrow();
        MapPlace hiddenPlace = createMapPlace("숨김 북마크 장소", "경상남도 진주시 숨김북마크로 1");
        hiddenPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(hiddenPlace);

        mockMvc.perform(post("/users/me/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("placeId", hiddenPlace.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));

        assertFalse(mapBookmarkRepository.existsByUserIdAndPlaceId(
                user.getId(),
                hiddenPlace.getId()
        ));
        assertFalse(placeRecommendationSnapshotRepository.existsById(hiddenPlace.getId()));
    }

    @Test
    void legacyBookmarkAndPlaceUploadPathsRemainSupported() throws Exception {
        String accessToken = signupAndLogin("legacyPathWriter01");

        MvcResult coordinateResult = mockMvc.perform(post("/map/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseLatitude", 35.1814,
                                "baseLongitude", 128.1084
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String coordinateToken = objectMapper.readTree(coordinateResult.getResponse().getContentAsString())
                .get("coordinateToken")
                .asText();

        mockMvc.perform(post("/map/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "레거시 업로드 장소",
                                "address", "경상남도 진주시 레거시업로드로 1",
                                "category", "풍경",
                                "imageUrl", "https://example.com/images/legacy-place.jpg",
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("레거시 업로드 장소"));

        MapPlace savedPlace = mapPlaceRepository.findAll().stream()
                .filter(place -> "레거시 업로드 장소".equals(place.getName()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("placeId", savedPlace.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(savedPlace.getId()));

        mockMvc.perform(delete("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("placeId", savedPlace.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(savedPlace.getId()));
    }

    @Test
    void likeAndUnlikeRefreshRecommendationSnapshot() throws Exception {
        String accessToken = signupAndLogin("reader09");
        MapPlace mapPlace = createMapPlace("좋아요 검증 장소", "경상남도 진주시 하대동 1", 35.1806, 128.1084, 1L);
        MapImage mapImage = createMapImage(mapPlace, 0L, "좋아요 검증 사진");

        mockMvc.perform(post("/map/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("mapImageId", mapImage.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapImageId").value(mapImage.getId()));

        PlaceRecommendationSnapshot likedSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(1L, likedSnapshot.getPhotoCount());
        assertEquals(1L, likedSnapshot.getTotalLikeCount());
        assertNotNull(likedSnapshot.getLatestPostCreatedAt());

        mockMvc.perform(delete("/map/like/{imageId}", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapImageId").value(mapImage.getId()));

        PlaceRecommendationSnapshot unlikedSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(1L, unlikedSnapshot.getPhotoCount());
        assertEquals(0L, unlikedSnapshot.getTotalLikeCount());
    }

    @Test
    void recommendPlacesAppliesDiversityReranking() throws Exception {
        String accessToken = signupAndLogin("reader07");

        MapPlace duplicatePlaceA = createMapPlace("중복 후보 A", "경상남도 진주시 평거동 10", 35.1802, 128.1079, 4L);
        MapPlace duplicatePlaceB = createMapPlace("중복 후보 B", "경상남도 진주시 평거동 11", 35.18025, 128.10795, 4L);
        MapPlace diversePlace = createMapPlace("다양성 후보", "경상남도 진주시 충무공동 1", 35.1865, 128.1145, 3L);

        createMapImage(duplicatePlaceA, 20L, "중복 A 사진 1");
        createMapImage(duplicatePlaceA, 15L, "중복 A 사진 2");
        createMapImage(duplicatePlaceA, 10L, "중복 A 사진 3");

        createMapImage(duplicatePlaceB, 19L, "중복 B 사진 1");
        createMapImage(duplicatePlaceB, 14L, "중복 B 사진 2");
        createMapImage(duplicatePlaceB, 9L, "중복 B 사진 3");

        createMapImage(diversePlace, 16L, "다양성 사진 1");
        createMapImage(diversePlace, 12L, "다양성 사진 2");
        createMapImage(diversePlace, 8L, "다양성 사진 3");

        mockMvc.perform(get("/places/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[*].name", containsInAnyOrder("중복 후보 A", "다양성 후보")));
    }

    private String signupAndLogin(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(username, username + "@example.com", "password123", 1998, null, "ko", "KR");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(username, "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }

    private String createCoordinateToken(String accessToken, String kakaoPlaceId, double latitude, double longitude) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("baseLatitude", latitude);
        payload.put("baseLongitude", longitude);
        if (kakaoPlaceId != null) {
            payload.put("kakaoPlaceId", kakaoPlaceId);
        }

        MvcResult coordinateResult = mockMvc.perform(post("/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(coordinateResult.getResponse().getContentAsString())
                .get("coordinateToken")
                .textValue();
    }

    private MapPlace createMapPlace(String name, String address) {
        return createMapPlace(name, address, 35.1801, 128.1078, 0L);
    }

    private MapPlace createMapPlace(String name, String address, String category, double latitude, double longitude) {
        return createMapPlace(name, address, category, latitude, longitude, 0L);
    }

    private MapPlace createMapPlace(String name, String address, double latitude, double longitude, long photoCount) {
        return createMapPlace(name, address, null, latitude, longitude, photoCount);
    }

    private MapPlace createMapPlace(String name, String address, Long userId) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(address)
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(userId)
                .registrant("placeOwner")
                .photoCount(0L)
                .build());
    }

    private MapPlace createMapPlace(
            String name,
            String address,
            String category,
            double latitude,
            double longitude,
            long photoCount
    ) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(address)
                .category(category)
                .latitude(latitude)
                .longitude(longitude)
                .userId(1L)
                .registrant("placeOwner")
                .photoCount(photoCount)
                .build());
    }

    private MapPlace createTouristMapPlace() {
        return mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .englishName("Jinju Castle")
                .address("경상남도 진주시 남강로 626")
                .category("관광")
                .touristSummary("진주의 대표 역사 관광지입니다.")
                .touristCategories(Set.of(TouristCategory.EXHIBITION, TouristCategory.OTHER))
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(1L)
                .registrant("placeOwner")
                .photoCount(0L)
                .build());
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password("password123")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
    }

    private void createBookmark(Long userId, Long placeId) {
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(placeId)
                .build());
    }

    private MapImage createMapImage(MapPlace mapPlace, long likeCount, String title) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/" + title + ".jpg")
                .s3Key("test/" + title + ".jpg")
                .title(title)
                .description(title + " 설명")
                .userId(99L)
                .username("placeOwner")
                .likeCount(likeCount)
                .mapPlace(mapPlace)
                .build());
    }

    private void createExposureLogs(Long placeId, int count, double latitude, double longitude) {
        for (int index = 0; index < count; index++) {
            placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                    .placeId(placeId)
                    .userId(1000L + index)
                    .requestLatitude(latitude)
                    .requestLongitude(longitude)
                    .ranking(1)
                    .recommendationVersion("place-rec-v1")
                    .build());
        }
    }

    private <T> T waitForValue(Supplier<T> supplier, java.util.function.Predicate<T> condition) {
        long deadline = System.currentTimeMillis() + 3_000L;
        T value = supplier.get();

        while (!condition.test(value) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("비동기 처리 대기 중 인터럽트가 발생했습니다.", exception);
            }
            value = supplier.get();
        }

        if (!condition.test(value)) {
            throw new AssertionError("비동기 처리 결과를 제한 시간 안에 확인하지 못했습니다.");
        }
        return value;
    }

    private void cleanupCommittedRecommendationTestData() {
        placeRecommendationFeatureLogRepository.deleteAll();
        placeRecommendationConversionRepository.deleteAll();
        placeRecommendationClickRepository.deleteAll();
        placeRecommendationExposureRepository.deleteAll();
        placeRecommendationSnapshotRepository.deleteAll();
        mapImageLikeRepository.deleteAll();
        mapBookmarkRepository.deleteAll();
        mapImageRepository.deleteAll();
        mapPlaceRepository.deleteAll();
    }

}
