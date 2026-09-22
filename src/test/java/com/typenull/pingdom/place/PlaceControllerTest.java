package com.typenull.pingdom.place;

import com.typenull.pingdom.place.api.PlaceController;
import com.typenull.pingdom.availability.domain.PlaceAvailability;
import com.typenull.pingdom.availability.infrastructure.PlaceAvailabilityRepository;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerProfile;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerification;
import com.typenull.pingdom.identity.domain.merchant.MerchantVerificationStatus;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerPlaceRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantOwnerProfileRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantPlaceInformationRepository;
import com.typenull.pingdom.identity.domain.repository.MerchantVerificationRepository;
import com.typenull.pingdom.offer.domain.TouristOffer;
import com.typenull.pingdom.offer.infrastructure.TouristOfferRepository;
import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import com.typenull.pingdom.place.infrastructure.persistence.event.PlaceEventRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import com.typenull.pingdom.shared.support.S3ObjectStorage.S3ObjectMetadata;
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
import org.junit.jupiter.api.Tag;
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
import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "abuse.rate-limit.redis-key-prefix=pingdom:test:place-controller:",
        "abuse.rate-limit.signup-email.limit=1000",
        "abuse.rate-limit.signup-ip.limit=1000",
        "abuse.rate-limit.login-ip.limit=1000"
})
@AutoConfigureMockMvc
@Transactional
class PlaceControllerTest {

    @TestConfiguration
    static class TestEmailSenderConfig {
        /**
         * 회원가입·로그인 흐름이 외부 메일 전송 없이 진행되도록 성공 결과를 반환하는 테스트 빈을 제공.
         */
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
    private MeterRegistry meterRegistry;

    @Autowired
    private PlaceController placeController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private MerchantOwnerProfileRepository merchantOwnerProfileRepository;

    @Autowired
    private MerchantVerificationRepository merchantVerificationRepository;

    @Autowired
    private MerchantOwnerPlaceRepository merchantOwnerPlaceRepository;

    @Autowired
    private MerchantPlaceInformationRepository merchantPlaceInformationRepository;

    @Autowired
    private PlaceEventRepository placeEventRepository;

    @Autowired
    private PlaceAvailabilityRepository placeAvailabilityRepository;

    @Autowired
    private TouristOfferRepository touristOfferRepository;

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
    private S3ObjectStorage s3ObjectStorage;

    /**
     * 이미지·추천 기록·영업 일정·장소·사용자를 비워 각 API 시나리오의 조회 및 집계 입력을 독립시킴.
     */
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

    /**
     * 테스트 후 별도 영업 일정 행을 정리하여 다음 시나리오에 자식 데이터가 남지 않게 함.
     */
    @AfterEach
    void tearDownOperatingScheduleRows() {
        clearOperatingScheduleRows();
    }

    /**
     * 예외 시간·예외 일정·정기 영업시간 순으로 삭제하여 장소 정리 전에 일정 참조를 제거.
     */
    private void clearOperatingScheduleRows() {
        jdbcTemplate.update("DELETE FROM map_place_operating_exception_hour");
        jdbcTemplate.update("DELETE FROM map_place_operating_exception");
        jdbcTemplate.update("DELETE FROM map_place_regular_operating_hour");
    }

    /**
     * 두 장소를 조회하면 최신 ID부터 반환하고 요청 페이지·제한·전체 건수와 다음 페이지 여부가 일치하는지 확인.
     */
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

    /**
     * 임시 휴업 장소가 일반·반경 목록, 자동완성, 상세, 사용자 북마크 조회에서 제외되는지 확인.
     */
    @Test
    void hidesTemporarilyClosedPlaces() throws Exception {
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

    /**
     * 영구 폐업 장소만 있을 때 컨트롤러 직접 호출의 추천 건수가 0인지 확인.
     */
    @Test
    void excludesPermanentlyClosedRecommendations() {
        MapPlace closedPlace = createMapPlace("추천 제외 장소", "경상남도 진주시 추천로 1", 35.1801, 128.1078, 1L);
        closedPlace.updateOperatingStatus(
                PlaceOperatingStatus.PERMANENTLY_CLOSED,
                LocalDateTime.of(2026, 7, 13, 10, 30)
        );
        mapPlaceRepository.saveAndFlush(closedPlace);

        var response = placeController.recommendPlaces(35.1801, 128.1078, 1, 5.0, null, null);

        assertEquals(0, response.getBody().recommendedCount());
    }

    /**
     * 101개 장소를 100개 제한으로 조회하면 최신 100개와 전체 2페이지·hasNext=true를 반환하는지 확인.
     */
    @Test
    void paginatesAtMaximumPlaceLimit() throws Exception {
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

    /**
     * 키워드 검색에도 100개 제한을 허용하고 일치하는 장소만 반환하는지 확인.
     */
    @Test
    void acceptsMaximumLimitWithKeyword() throws Exception {
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

    /**
     * 위도가 91도인 장소를 목록과 전체 건수에서 제외하고 정상 좌표 장소만 반환하는지 확인.
     */
    @Test
    void excludesInvalidCoordinatePlaces() throws Exception {
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

    /**
     * 주소 키워드와 공백을 포함한 카테고리를 함께 적용하고 일치 장소의 정규화 주소·지오코딩 출처를 반환하는지 확인.
     */
    @Test
    void filtersAddressAndCategory() throws Exception {
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

    /**
     * 공백·소문자를 포함한 관광 카테고리 입력을 정규화하여 K_POP 장소만 반환하는지 확인.
     */
    @Test
    void filtersByTouristCategory() throws Exception {
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

    /**
     * POPULAR 정렬에서 사진 수가 더 많은 장소를 먼저 반환하고 전체 건수를 유지하는지 확인.
     */
    @Test
    void sortsPopularByPhotoCount() throws Exception {
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

    /**
     * HIDDEN 장소가 목록·자동완성·상세·사용자 북마크에서 제외되는지 확인.
     */
    @Test
    void excludesHiddenDiscoveryPlaces() throws Exception {
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

    /**
     * 카드에 관광 요약·출처·검증 기본값을 반환하고 숨김·영구 폐업은 404, 임시 휴업은 currentlyOperating=false로 제공하는지 확인.
     */
    @Test
    void returnsCardByVisibilityStatus() throws Exception {
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

    /**
     * 카드 조회의 무인증 요청은 401, 인증 후 존재하지 않는 장소는 PLACE_NOT_FOUND 404로 구분하는지 확인.
     */
    @Test
    void rejectsUnauthenticatedAndMissingCards() throws Exception {
        mockMvc.perform(get("/places/{placeId}/card", 999_999_999L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String accessToken = signupAndLogin("touristPlaceCardFailure" + Long.toUnsignedString(System.nanoTime()));

        mockMvc.perform(get("/places/{placeId}/card", 999_999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    /**
     * 1km 반경 밖 장소를 제외하고 포함된 두 장소를 가까운 순으로 반환하며 거리 값을 제공하는지 확인.
     */
    @Test
    void filtersRadiusAndSortsNearest() throws Exception {
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

    /**
     * 위도만 전달한 불완전한 거리 조건을 PLACE_SEARCH_CONDITION_INVALID 400으로 거절하는지 확인.
     */
    @Test
    void rejectsIncompleteDistanceCondition() throws Exception {
        String accessToken = signupAndLogin("readerSearch03");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_CONDITION_INVALID"));
    }

    /**
     * 좌표 없는 NEAREST 정렬 요청을 거리 조건 오류로 거절하는지 확인.
     */
    @Test
    void rejectsNearestWithoutCoordinates() throws Exception {
        String accessToken = signupAndLogin("readerSearch04");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sort", "NEAREST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_CONDITION_INVALID"));
    }

    /**
     * 경도 Infinity를 포함한 거리 검색 요청이 400을 반환하는지 확인.
     */
    @Test
    void rejectsInfiniteLongitude() throws Exception {
        String accessToken = signupAndLogin("readerSearch05");

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "Infinity")
                        .param("radiusKm", "1.0")
                        .param("sort", "NEAREST"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 지원하지 않는 RATING 정렬 입력을 전용 오류 코드와 400으로 거절하는지 확인.
     */
    @Test
    void rejectsUnsupportedPlaceSort() throws Exception {
        String accessToken = signupAndLogin("readerUnsupportedSort" + Long.toUnsignedString(System.nanoTime()));

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sort", "RATING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PLACE_SEARCH_SORT"));
    }

    /**
     * 정의되지 않은 관광 카테고리를 장소 검색 조건 오류와 400으로 거절하는지 확인.
     */
    @Test
    void rejectsUnsupportedTouristCategory() throws Exception {
        String accessToken = signupAndLogin("readerUnsupportedTouristCategory" + Long.toUnsignedString(System.nanoTime()));

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("touristCategory", "NOT_A_TOURIST_CATEGORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_CONDITION_INVALID"));
    }

    /**
     * 인증 토큰이 없는 장소 목록 요청에 INVALID_TOKEN 401을 반환하는지 확인.
     */
    @Test
    void rejectsUnauthenticatedPlaceList() throws Exception {
        mockMvc.perform(get("/places"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 상세 조회가 저장한 장소의 ID·이름·주소·등록자를 반환하는지 확인.
     */
    @Test
    void returnsStoredPlaceDetail() throws Exception {
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

    /**
     * 보충 정보가 없는 장소의 방문 판단 응답에서 상점 정보와 행사·예약·혜택 목록은 비고 장소 및 확인 시각은 제공되는지 확인.
     */
    @Test
    void returnsEmptyVisitDecisionSupplements() throws Exception {
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

    /**
     * 승인된 Merchant의 예약 URL, 진행 중 공개 행사, 예약 가능 수량, 공개 혜택을 한 응답에 합치고 정보 수정자 ID는 숨기는지 확인.
     */
    @Test
    void combinesPublishedVisitDecisionData() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReaderAggregate");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        MapPlace mapPlace = createMapPlace("통합 방문 결정 장소", "경상남도 진주시 방문로 10");
        User merchant = createActiveMerchantForVisitDecision(mapPlace, now);

        merchantPlaceInformationRepository.saveAndFlush(MerchantPlaceInformation.create(
                mapPlace.getId(),
                "관광객용 장소 소개",
                "010-1111-2222",
                "https://pingdom.test/places/10",
                "https://pingdom.test/places/10/reservations",
                merchant.getId(),
                now
        ));

        PlaceEvent event = PlaceEvent.create(
                mapPlace,
                "진행 중인 팝업",
                "오늘만 진행합니다.",
                PlaceEventType.POP_UP,
                now.minusHours(1),
                now.plusHours(1),
                now.minusHours(2)
        );
        event.publish(now.minusMinutes(30));
        placeEventRepository.saveAndFlush(event);

        placeAvailabilityRepository.saveAndFlush(PlaceAvailability.create(
                merchant.getId(), mapPlace.getId(), now.plusHours(1), now.plusHours(2), 4, now
        ));

        TouristOffer offer = TouristOffer.draft(
                merchant.getId(), mapPlace.getId(), "방문 결정 혜택", "관광객 한정 혜택", "음료 1잔 무료",
                now.minusHours(1), now.plusDays(1), 5, 3, now.minusHours(2)
        );
        offer.publish(now.minusMinutes(30));
        touristOfferRepository.saveAndFlush(offer);

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantInformation.reservationUrl")
                        .value("https://pingdom.test/places/10/reservations"))
                .andExpect(jsonPath("$.merchantInformation.updatedByUserId").doesNotExist())
                .andExpect(jsonPath("$.ongoingEvents.length()").value(1))
                .andExpect(jsonPath("$.ongoingEvents[0].title").value("진행 중인 팝업"))
                .andExpect(jsonPath("$.reservableAvailabilities.length()").value(1))
                .andExpect(jsonPath("$.reservableAvailabilities[0].remainingCapacity").value(4))
                .andExpect(jsonPath("$.availableOffers.offers.length()").value(1))
                .andExpect(jsonPath("$.availableOffers.offers[0].title").value("방문 결정 혜택"));
    }

    /**
     * 아직 시작하지 않은 공개 행사와 현재 기간의 초안 행사를 방문 판단의 진행 중 목록에서 제외하는지 확인.
     */
    @Test
    void excludesScheduledAndDraftEvents() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReaderEventFilter");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        MapPlace mapPlace = createMapPlace("이벤트 필터 장소", "경상남도 진주시 방문로 11");

        PlaceEvent scheduledEvent = PlaceEvent.create(
                mapPlace, "예정 이벤트", "내일 진행합니다.", PlaceEventType.EXHIBITION,
                now.plusHours(1), now.plusHours(2), now.minusHours(1)
        );
        scheduledEvent.publish(now);
        placeEventRepository.saveAndFlush(scheduledEvent);
        placeEventRepository.saveAndFlush(PlaceEvent.create(
                mapPlace, "초안 이벤트", "아직 공개되지 않았습니다.", PlaceEventType.PERFORMANCE,
                now.minusMinutes(30), now.plusMinutes(30), now.minusHours(1)
        ));

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ongoingEvents.length()").value(0));
    }

    /**
     * 비활성 예약 슬롯과 초안 혜택을 방문 판단의 예약·혜택 목록에서 제외하는지 확인.
     */
    @Test
    void excludesInactiveSlotsAndDraftOffers() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReaderCommerceFilter");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        MapPlace mapPlace = createMapPlace("전환 데이터 필터 장소", "경상남도 진주시 방문로 12");
        User merchant = createActiveMerchantForVisitDecision(mapPlace, now);

        PlaceAvailability inactiveAvailability = PlaceAvailability.create(
                merchant.getId(), mapPlace.getId(), now.plusHours(1), now.plusHours(2), 3, now
        );
        inactiveAvailability.deactivate(now);
        placeAvailabilityRepository.saveAndFlush(inactiveAvailability);
        touristOfferRepository.saveAndFlush(TouristOffer.draft(
                merchant.getId(), mapPlace.getId(), "초안 혜택", "공개 전 혜택", "혜택",
                now.minusHours(1), now.plusDays(1), 3, 3, now.minusHours(2)
        ));

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservableAvailabilities.length()").value(0))
                .andExpect(jsonPath("$.availableOffers.offers.length()").value(0));
    }

    /**
     * 장소에 연결된 Merchant가 REVOKED이면 기존 상점 정보를 방문 판단 응답에 노출하지 않는지 확인.
     */
    @Test
    void hidesRevokedOwnerInformation() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReaderRevokedMerchant");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        MapPlace mapPlace = createMapPlace("회수 Merchant 장소", "경상남도 진주시 방문로 13");
        User merchant = userRepository.saveAndFlush(User.builder()
                .username("revokedMerchant" + Long.toUnsignedString(System.nanoTime()))
                .email("revoked-merchant-" + Long.toUnsignedString(System.nanoTime()) + "@pingdom.test")
                .password("password123")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.MERCHANT_OWNER)
                .build());
        merchantOwnerProfileRepository.saveAndFlush(MerchantOwnerProfile.builder()
                .userId(merchant.getId())
                .businessName("회수 상점")
                .displayName("회수 Merchant")
                .contactEmail("revoked@pingdom.test")
                .contactPhone("010-9999-9999")
                .status(MerchantOwnerStatus.REVOKED)
                .createdAt(now)
                .updatedAt(now)
                .build());
        merchantOwnerPlaceRepository.saveAndFlush(MerchantOwnerPlace.builder()
                .merchantOwnerUserId(merchant.getId())
                .placeId(mapPlace.getId())
                .createdAt(now)
                .build());
        merchantPlaceInformationRepository.saveAndFlush(MerchantPlaceInformation.create(
                mapPlace.getId(), "노출되면 안 되는 Merchant 정보", "010-9999-9999",
                "https://pingdom.test/revoked", "https://pingdom.test/revoked/reservations", merchant.getId(), now
        ));

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantInformation").isEmpty());
    }

    /**
     * 운영 중 장소의 방문 판단 조회 성공 시 해당 상태 태그의 조회 Counter가 정확히 1 증가하는지 확인.
     */
    @Test
    void countsSuccessfulVisitDecisionViews() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReaderMetric");
        MapPlace mapPlace = createMapPlace("관측 방문 결정 장소", "경상남도 진주시 방문로 14");
        Counter counter = meterRegistry.find("pingdom.place.visit_decision_views")
                .tag("operating_status", "OPERATING")
                .counter();
        double before = counter == null ? 0.0d : counter.count();

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertEquals(
                before + 1.0d,
                meterRegistry.get("pingdom.place.visit_decision_views")
                        .tag("operating_status", "OPERATING")
                        .counter()
                        .count()
        );
    }

    /**
     * 임시 휴업 장소의 방문 판단은 조회를 허용하되 operatingStatus와 currentlyOperating=false를 반환하는지 확인.
     */
    @Test
    void includesTemporarilyClosedVisitDecision() throws Exception {
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

    /**
     * 영구 폐업 장소의 방문 판단 요청을 PLACE_NOT_FOUND 404로 거절하는지 확인.
     */
    @Test
    void rejectsPermanentlyClosedVisitDecision() throws Exception {
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

    /**
     * 인증 없는 방문 판단 요청을 INVALID_TOKEN 401로 거절하는지 확인.
     */
    @Test
    void rejectsUnauthenticatedVisitDecision() throws Exception {
        MapPlace mapPlace = createMapPlace("인증 필요 방문 결정 장소", "경상남도 진주시 방문로 4");

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 탐색 상태가 HIDDEN인 장소의 방문 판단 요청을 PLACE_NOT_FOUND 404로 거절하는지 확인.
     */
    @Test
    void rejectsHiddenVisitDecision() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReader04");
        MapPlace mapPlace = createMapPlace("숨김 방문 결정 장소", "경상남도 진주시 방문로 5");
        mapPlace.updateDiscoveryStatus(PlaceDiscoveryStatus.HIDDEN);
        mapPlaceRepository.saveAndFlush(mapPlace);

        mockMvc.perform(get("/places/{placeId}/visit-decision", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    /**
     * 존재하지 않는 장소의 방문 판단 요청을 PLACE_NOT_FOUND 404로 거절하는지 확인.
     */
    @Test
    void rejectsMissingVisitDecisionPlace() throws Exception {
        String accessToken = signupAndLogin("visitDecisionReader05");

        mockMvc.perform(get("/places/{placeId}/visit-decision", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    /**
     * 로그인 사용자가 북마크한 두 장소만 최신 순으로 반환하고 페이지 건수가 일치하는지 확인.
     */
    @Test
    void listsBookmarkedPlacesNewestFirst() throws Exception {
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

    /**
     * 장소는 있어도 사용자 북마크가 없으면 장소·건수·전체 페이지가 모두 0이고 다음 페이지가 없는지 확인.
     */
    @Test
    void returnsEmptyBookmarkPage() throws Exception {
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

    /**
     * 제거된 /place 및 /users/bookmarks 경로가 인증된 요청에도 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyPlaceAndBookmarkPaths() throws Exception {
        String accessToken = signupAndLogin("legacyPathReader01");

        mockMvc.perform(get("/place")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/users/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isNotFound());
    }

    /**
     * 실제 장소가 존재해도 제거된 /place/{id} 상세 경로에는 매핑이 없는지 확인.
     */
    @Test
    void rejectsLegacyPlaceDetailPath() throws Exception {
        String accessToken = signupAndLogin("legacyPlaceDetail" + Long.toUnsignedString(System.nanoTime()));
        MapPlace place = createMapPlace("구 장소 상세", "경상남도 진주시 호환로 1");

        mockMvc.perform(get("/place/{id}", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * 직접 등록 경로 두 개는 404, 숫자 ID 경로의 지원하지 않는 POST는 405로 구분하는지 확인.
     */
    @Test
    void rejectsRemovedPlaceCreationRoutes() throws Exception {
        String accessToken = signupAndLogin("removedDirectPlaceCreation");

        mockMvc.perform(post("/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/places/{id}", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * 영문 이름으로 일반·반경 목록과 자동완성을 검색하고 목록·상세에서 관광 요약·카테고리를 반환하는지 확인.
     */
    @Test
    void exposesTouristInformationAcrossQueries() throws Exception {
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

    /**
     * 상세 응답이 요일별 영업시간과 날짜별 휴무·대체 영업시간을 정해진 필드와 순서로 제공하는지 확인.
     */
    @Test
    void returnsOperatingScheduleDetails() throws Exception {
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

    /**
     * 같은 키워드가 지번 주소와 다른 장소의 카테고리에 일치하면 지번 주소 장소를 자동완성 상위에 두는지 확인.
     */
    @Test
    void ranksJibunMatchAboveCategory() throws Exception {
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

    /**
     * 공백을 포함한 coffee 별칭을 카페로 정규화하여 카페 장소만 검색하는지 확인.
     */
    @Test
    void normalizesCategoryAliasForSearch() throws Exception {
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

    /**
     * 존재하지 않는 장소 상세 요청을 PLACE_NOT_FOUND 404로 반환하는지 확인.
     */
    @Test
    void rejectsMissingPlaceDetail() throws Exception {
        String accessToken = signupAndLogin("reader03");

        mockMvc.perform(get("/places/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    /**
     * 실제 객체 메타데이터가 확인된 S3 키로 탐색 미디어를 생성하고 요청 URL 대신 저장소 URL·용도·기본 순서를 반환하는지 확인.
     */
    @Test
    void createsExplorationMediaFromStorageKey() throws Exception {
        String accessToken = signupAndLogin("placeMediaOwner01");
        Long ownerId = userRepository.findByUsername("placeMediaOwner01").orElseThrow().getId();
        MapPlace place = createMapPlace("탐색 미디어 장소", "경상남도 진주시 미디어로 1", ownerId);
        String s3Key = "places/%d/exploration/%d/issued.jpg".formatted(place.getId(), ownerId);
        when(s3ObjectStorage.headObject(s3Key)).thenReturn(new S3ObjectMetadata(1_024L, "image/jpeg"));
        when(s3ObjectStorage.publicUrl(s3Key)).thenReturn("https://s3.pingdom.test/" + s3Key);

        mockMvc.perform(post("/places/{id}/media/exploration", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "imageUrl", "https://untrusted.example/exploration.jpg",
                                "s3Key", s3Key,
                                "thumbnailUrl", "https://cdn.pingdom.test/exploration-thumb.jpg",
                                "thumbnailS3Key", "places/exploration-thumb.jpg"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(place.getId()))
                .andExpect(jsonPath("$.purpose").value("EXPLORATION"))
                .andExpect(jsonPath("$.imageUrl").value("https://s3.pingdom.test/" + s3Key))
                .andExpect(jsonPath("$.sourceMapImageId").isEmpty())
                .andExpect(jsonPath("$.displayOrder").value(0));
    }

    /**
     * 탐색·검증 미디어 조회가 각 용도의 항목만 반환하고 원본 게시물 ID는 검증 미디어에만 포함하는지 확인.
     */
    @Test
    void separatesMediaByPurpose() throws Exception {
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

    /**
     * URL만 있고 S3 키가 없는 탐색 미디어 생성 요청을 필드 검증 오류로 거절하는지 확인.
     */
    @Test
    void rejectsMissingExplorationStorageKey() throws Exception {
        String accessToken = signupAndLogin("placeMediaOwner03");
        Long ownerId = userRepository.findByUsername("placeMediaOwner03").orElseThrow().getId();
        MapPlace place = createMapPlace("미디어 검증 장소", "경상남도 진주시 검증로 1", ownerId);

        mockMvc.perform(post("/places/{id}/media/exploration", place.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("imageUrl", "https://untrusted.example/image.jpg"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.s3Key").value("s3Key는 필수입니다."));
    }

    /**
     * 탐색 미디어가 저장되어 있어도 숨김 장소의 공개 조회를 PLACE_NOT_FOUND 404로 거절하는지 확인.
     */
    @Test
    void rejectsHiddenPlaceExplorationMedia() throws Exception {
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

    /**
     * 다른 사용자의 검증 미디어 조회는 403, 장소 등록자의 조회는 빈 목록 200으로 허용하는지 확인.
     */
    @Test
    void restrictsVerificationMediaToRegistrant() throws Exception {
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

    /**
     * 탐색 미디어 삭제 후 해당 행만 없어지고 같은 장소의 검증 미디어는 유지되는지 확인.
     */
    @Test
    void preservesVerificationMediaWhenDeletingExploration() throws Exception {
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

    /**
     * 컨트롤러를 null principal로 직접 호출해도 추천 1건과 요청 ID를 반환하는지 확인. HTTP 보안 경로는 검증 범위에서 제외.
     */
    @Test
    void recommendsWithNullPrincipal() {
        MapPlace mapPlace = createMapPlace("비로그인 추천 장소", "경상남도 진주시 익명로 1", 35.1801, 128.1078, 1L);
        createMapImage(mapPlace, 0L, "비로그인 추천 사진");

        var response = placeController.recommendPlaces(35.1801, 128.1078, 1, 5.0, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().recommendedCount());
        assertNotNull(response.getBody().recommendationRequestId());
        assertEquals("비로그인 추천 장소", response.getBody().places().get(0).name());
    }

    /**
     * 북마크와 사진 반응이 있는 입력에서 개인화 장소가 우선되고 정책 버전·요청 ID·개인화 사유 코드를 반환하는지 확인.
     */
    @Test
    void returnsPersonalizedNearbyRecommendations() throws Exception {
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

    /**
     * 두 사용자가 seed와 함께 북마크한 장소를 더 가까운 일반 장소보다 먼저 추천하는지 확인.
     */
    @Test
    void ranksBySharedBookmarkSimilarity() throws Exception {
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

    /**
     * 현재 위치는 서울이고 북마크는 진주에 있을 때 위치 반경 밖의 개인화 확장 장소도 추천되는지 확인.
     */
    @Test
    void includesDistantPersonalizedCandidates() throws Exception {
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

    /**
     * 개인화 신호가 없는 사용자는 주변 후보 중 사진·좋아요가 많은 장소를 먼저 받고 주변 추천 사유를 받는지 확인.
     */
    @Test
    void ranksPopularWithoutUserSignals() throws Exception {
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

    /**
     * 현재 위치 주변에 후보가 없어도 최근 집계 스냅샷이 있는 원거리 트렌드 장소가 추천되는지 확인.
     */
    @Test
    void includesTrendsWithoutNearbyCandidates() throws Exception {
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

    /**
     * 추천 요청 트랜잭션을 실제 커밋한 뒤 비동기 노출 2건의 순위·요청 정보와 각 스냅샷 노출 수 증가를 기다려 확인.
     */
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

    /**
     * 위치와 사진 반응이 같은 두 후보 중 기존 노출 30건 장소보다 저노출 장소를 우선 선택하는지 확인.
     */
    @Test
    void prefersLowerExposurePlace() throws Exception {
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

    /**
     * 클릭 요청이 201을 반환하고 장소·요청 ID·시각을 저장하며 새 스냅샷의 클릭은 1, 노출은 0으로 유지하는지 확인.
     */
    @Test
    void recordsClickAndUpdatesSnapshot() throws Exception {
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

    /**
     * 숨김 장소 클릭을 PLACE_NOT_FOUND 404로 거절하고 클릭 기록과 추천 스냅샷을 생성하지 않는지 확인.
     */
    @Test
    void rejectsClicksOnHiddenPlaces() throws Exception {
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

    /**
     * 실험 버전 추천을 커밋하고 노출 기록을 기다린 뒤 클릭·북마크를 수행하면 전환이 클릭·특성 로그에 귀속되고 버전이 일치하는지 확인.
     */
    @Test
    void attributesBookmarkToRecommendationClick() throws Exception {
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

    /**
     * place-rec-v2를 명시한 추천을 커밋한 뒤 특성 로그와 비동기 노출 기록이 응답의 버전·요청 ID를 유지하는지 확인.
     */
    @Test
    void recordsExplicitExperimentalVersion() throws Exception {
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

    /**
     * 추천 응답의 요청 ID와 실험 버전을 클릭 요청에 전달하면 동일 값으로 클릭 기록이 저장되는지 확인.
     */
    @Test
    void preservesRecommendationRequestInClick() throws Exception {
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

    /**
     * 같은 요청 ID에 두 사용자의 특성 로그가 있어도 각 사용자는 본인 장소와 점수 설명만 조회하는지 확인.
     */
    @Test
    void scopesExplanationsToCurrentUser() throws Exception {
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

    /**
     * 설명 로그가 없는 요청 ID에는 RECOMMENDATION_EXPLANATION_NOT_FOUND 404를 반환하는지 확인.
     */
    @Test
    void rejectsMissingRecommendationExplanation() throws Exception {
        String accessToken = signupAndLogin("reader22");

        mockMvc.perform(get("/places/recommendations/{requestId}/explanation", "missing-request-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EXPLANATION_NOT_FOUND"));
    }

    /**
     * 인증 없이 추천 설명을 조회하면 401을 반환하는지 확인.
     */
    @Test
    void rejectsUnauthenticatedRecommendationExplanation() throws Exception {
        mockMvc.perform(get("/places/recommendations/{requestId}/explanation", "missing-request-id"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 제거된 단수형 /place 추천·클릭·설명 경로가 모두 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyRecommendationPaths() throws Exception {
        String accessToken = signupAndLogin("removedLegacyRecommendation");

        mockMvc.perform(get("/place/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/place/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", 1L,
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "legacy-unauthorized-test"
                        ))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/place/recommendations/{requestId}/explanation", "legacy-unauthorized-test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * 제거된 /map/places/coordinates 경로에 좌표를 전송해도 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyCoordinateRoute() throws Exception {
        String accessToken = signupAndLogin("removedLegacyCoordinate");

        mockMvc.perform(post("/map/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseLatitude", 35.1814,
                                "baseLongitude", 128.1084
                        ))))
                .andExpect(status().isNotFound());
    }

    /**
     * 제거된 /map/places/upload 경로에 등록 본문을 전송해도 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyUploadRoute() throws Exception {
        String accessToken = signupAndLogin("removedLegacyUpload");

        mockMvc.perform(post("/map/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "무인증 레거시 장소",
                                "address", "경상남도 진주시 테스트로 1",
                                "category", "풍경",
                                "imageUrl", "https://example.com/images/legacy-place.jpg",
                                "coordinateToken", "invalid-token"
                        ))))
                .andExpect(status().isNotFound());
    }

    /**
     * 제거된 /map/places/{id}/delete 경로가 인증 요청에도 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyDeleteRoute() throws Exception {
        String accessToken = signupAndLogin("removedLegacyDelete");

        mockMvc.perform(delete("/map/places/{id}/delete", 1L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * 동일 노출 20건에서 클릭 6건인 후보를 클릭 0건인 후보보다 우선하고 클릭 반응 추천 사유를 반환하는지 확인.
     */
    @Test
    void prefersStrongerClickResponse() throws Exception {
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

    /**
     * 1회 노출·1회 클릭 후보보다 20회 노출·6회 클릭 후보를 우선하여 작은 표본의 단순 CTR 과대평가를 방지하는지 확인.
     */
    @Test
    void downweightsSingleClickSample() throws Exception {
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

    /**
     * 클릭·노출이 같은 두 후보 중 북마크·좋아요 전환이 있는 장소를 우선하고 저장 전환 사유를 반환하는지 확인.
     */
    @Test
    void prefersHigherConversionQuality() throws Exception {
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

    /**
     * 북마크 생성 시 스냅샷 북마크 수가 1로 증가하고 삭제 시 0으로 돌아가며 좋아요 수는 유지되는지 확인.
     */
    @Test
    void refreshesSnapshotForBookmarkChanges() throws Exception {
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

    /**
     * 숨김 장소 북마크 생성을 PLACE_NOT_FOUND 404로 거절하고 북마크와 추천 스냅샷을 만들지 않는지 확인.
     */
    @Test
    void rejectsBookmarkingHiddenPlace() throws Exception {
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

    /**
     * 제거된 /map/bookmarks 생성·삭제 요청이 모두 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyBookmarkWrites() throws Exception {
        String accessToken = signupAndLogin("legacyPathWriter01");

        mockMvc.perform(post("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("placeId", 1L))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("placeId", "1"))
                .andExpect(status().isNotFound());
    }

    /**
     * 장소 필드를 갖춘 요청 본문을 보내도 제거된 업로드 경로가 404를 반환하는지 확인.
     */
    @Test
    void rejectsLegacyUploadWithBody() throws Exception {
        String accessToken = signupAndLogin("legacyPlaceUploadBlocked01");

        mockMvc.perform(post("/map/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "차단된 레거시 장소",
                                "address", "경상남도 진주시 차단레거시로 1",
                                "category", "풍경",
                                "imageUrl", "https://example.com/images/blocked-place.jpg",
                                "coordinateToken", "removed-legacy-token"
                        ))))
                .andExpect(status().isNotFound());
    }

    /**
     * 서로 가까운 인기 후보 둘을 함께 선택하지 않고 떨어진 다양성 후보를 포함한 2개 결과를 반환하는지 확인.
     */
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

    /**
     * 회원가입과 로그인 API의 성공을 확인하고 보호된 장소 요청에 사용할 accessToken을 반환.
     */
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

    /**
     * 활성 Merchant 계정·승인된 본인 및 사업자 검증·장소 소유 연결을 저장해 방문 판단의 공개 자격을 준비.
     */
    private User createActiveMerchantForVisitDecision(MapPlace mapPlace, LocalDateTime now) {
        String suffix = Long.toUnsignedString(System.nanoTime());
        User merchant = userRepository.saveAndFlush(User.builder()
                .username("visitDecisionMerchant" + suffix)
                .email("visit-decision-merchant-" + suffix + "@example.com")
                .password("password123")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.MERCHANT_OWNER)
                .build());
        merchantOwnerProfileRepository.saveAndFlush(MerchantOwnerProfile.builder()
                .userId(merchant.getId())
                .businessName("방문 결정 상점")
                .displayName("방문 결정 Merchant")
                .contactEmail("merchant@pingdom.test")
                .contactPhone("010-1111-2222")
                .status(MerchantOwnerStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
        merchantVerificationRepository.saveAndFlush(MerchantVerification.builder()
                .userId(merchant.getId())
                .legalName("핑덤 Merchant")
                .businessName("방문 결정 상점")
                .encryptedBusinessRegistrationNumber("encrypted-registration")
                .identityStatus(MerchantVerificationStatus.APPROVED)
                .businessStatus(MerchantVerificationStatus.APPROVED)
                .createdAt(now)
                .updatedAt(now)
                .build());
        merchantOwnerPlaceRepository.saveAndFlush(MerchantOwnerPlace.builder()
                .merchantOwnerUserId(merchant.getId())
                .placeId(mapPlace.getId())
                .createdAt(now)
                .build());
        return merchant;
    }

    /**
     * 이름과 주소만 필요한 테스트에 기본 진주 좌표와 사진 수 0인 장소를 저장.
     */
    private MapPlace createMapPlace(String name, String address) {
        return createMapPlace(name, address, 35.1801, 128.1078, 0L);
    }

    /**
     * 카테고리·좌표가 필요한 검색 테스트에 사진 수 0인 장소를 저장.
     */
    private MapPlace createMapPlace(String name, String address, String category, double latitude, double longitude) {
        return createMapPlace(name, address, category, latitude, longitude, 0L);
    }

    /**
     * 추천 비교에 사용할 좌표·사진 수를 받되 카테고리는 지정하지 않은 장소를 저장.
     */
    private MapPlace createMapPlace(String name, String address, double latitude, double longitude, long photoCount) {
        return createMapPlace(name, address, null, latitude, longitude, photoCount);
    }

    /**
     * 미디어 관리 권한 비교를 위해 지정한 등록 사용자 ID를 가진 장소를 저장.
     */
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

    /**
     * 검색·추천 조건에서 바꾸는 카테고리·위경도·사진 수를 받아 공통 등록자 정보와 함께 저장.
     */
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

    /**
     * 영문 검색과 관광 정보 직렬화를 검증할 영문 이름·요약·복수 관광 카테고리 장소를 저장.
     */
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

    /**
     * 공동 북마크 신호를 만들기 위한 사용자를 API 로그인 없이 직접 저장.
     */
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

    /**
     * 사용자와 장소의 북마크 연결을 저장하여 추천 유사도 입력을 생성.
     */
    private void createBookmark(Long userId, Long placeId) {
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(placeId)
                .build());
    }

    /**
     * 지정한 좋아요 수를 가진 장소 게시물을 저장하여 추천 품질과 미디어 연결 입력을 생성.
     */
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

    /**
     * 서로 다른 사용자 ID로 지정 수만큼 노출 기록을 저장해 저노출 후보와 기존 노출 후보를 비교.
     */
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

    /**
     * 커밋 후 비동기 저장 결과를 50ms 간격으로 최대 3초 조회하며 조건 미충족이나 인터럽트는 assertion 실패로 전환.
     */
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

    /**
     * 롤백되지 않는 추천 시나리오가 끝난 뒤 특성·전환·클릭·노출·스냅샷과 관련 장소 데이터를 직접 정리.
     */
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
