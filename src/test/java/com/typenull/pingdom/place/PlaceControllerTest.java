package com.typenull.pingdom.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.api.PlaceController;
import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationExposure;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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
        mapImageRepository.deleteAllInBatch();
        placeRecommendationConversionRepository.deleteAllInBatch();
        placeRecommendationClickRepository.deleteAllInBatch();
        placeRecommendationExposureRepository.deleteAllInBatch();
        placeRecommendationFeatureLogRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
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
        String accessToken = signupAndLogin("readerSearch01");
        MapPlace matchingPlace = createMapPlace(
                "진주성",
                "경상남도 진주시 남강로 626",
                "관광",
                35.1894,
                128.0789
        );
        createMapPlace("남강 카페", "경상남도 진주시 남강로 10", "카페", 35.1801, 128.1078);

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("keyword", "남강로")
                        .param("category", " 관광 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].id").value(matchingPlace.getId()))
                .andExpect(jsonPath("$.places[0].category").value("관광"))
                .andExpect(jsonPath("$.totalCount").value(1));
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
                .andExpect(jsonPath("$.geocodingSource").value("KAKAO"))
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
                .andExpect(jsonPath("$.places[0].reason").value("저장한 장소와 가까운 추천 장소입니다."));
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
    void recordRecommendationClickStoresRawLogAndIncreasesSnapshotCount() throws Exception {
        String accessToken = signupAndLogin("reader12");
        MapPlace clickedPlace = createMapPlace("클릭 장소", "경상남도 진주시 클릭로 1", 35.1803, 128.1079, 1L);
        createMapImage(clickedPlace, 2L, "클릭 사진");

        mockMvc.perform(post("/places/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", clickedPlace.getId(),
                                "recommendationVersion", "place-rec-v1"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(clickedPlace.getId()))
                .andExpect(jsonPath("$.message").value("추천 장소 클릭을 기록했습니다."));

        List<PlaceRecommendationClick> clicks = placeRecommendationClickRepository.findAll();
        assertEquals(1, clicks.size());
        assertEquals(clickedPlace.getId(), clicks.get(0).getPlaceId());
        assertNotNull(clicks.get(0).getCreatedAt());
        assertNull(clicks.get(0).getRequestId());

        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(clickedPlace.getId())
                .orElseThrow();
        assertEquals(1L, snapshot.getClickCount());
        assertEquals(0L, snapshot.getExposureCount());
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
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v1"))
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
                                "recommendationVersion", "place-rec-v1",
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
        assertEquals("place-rec-v1", conversions.get(0).getRecommendationVersion());
        assertNotNull(conversions.get(0).getCreatedAt());
        assertNotNull(conversions.get(0).getPlaceRecommendationClickId());

        List<PlaceRecommendationExposure> exposures = waitForValue(
                placeRecommendationExposureRepository::findAll,
                loaded -> loaded.size() == 1
        );
        assertEquals(1, exposures.size());
        assertEquals("place-rec-v1", exposures.get(0).getRecommendationVersion());

        List<PlaceRecommendationClick> clicks = placeRecommendationClickRepository.findAll();
        assertEquals(1, clicks.size());
        assertEquals("place-rec-v1", clicks.get(0).getRecommendationVersion());
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
                                "recommendationVersion", "place-rec-v1"
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
                                "recommendationVersion", "place-rec-v1"
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
