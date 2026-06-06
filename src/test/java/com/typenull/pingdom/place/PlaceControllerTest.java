package com.typenull.pingdom.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.MapBookmark;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.PlaceRecommendationExposure;
import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
@Transactional
class PlaceControllerTest {

    @TestConfiguration
    static class TestEmailSenderConfig {
        @Bean
        @Primary
        EmailSender emailSender() {
            return (recipientEmail, verificationCode) -> {};
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

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
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listPlacesReturnsPagedPlaces() throws Exception {
        String accessToken = signupAndLogin("reader01");
        createMapPlace("첫 번째 장소", "경상남도 진주시 진양호로 1");
        createMapPlace("두 번째 장소", "경상남도 진주시 남강로 2");

        mockMvc.perform(get("/place")
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
    void getPlaceReturnsPlaceDetailOnly() throws Exception {
        String accessToken = signupAndLogin("reader02");
        MapPlace mapPlace = createMapPlace("진주성", "경상남도 진주시 남강로 626");

        mockMvc.perform(get("/place/{id}", mapPlace.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapPlace.getId()))
                .andExpect(jsonPath("$.name").value("진주성"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.registrant").value("placeOwner"));
    }

    @Test
    void uploadPlaceStoresImageUrl() throws Exception {
        String accessToken = signupAndLogin("placeUploader01");
        String coordinateToken = createCoordinateToken(accessToken, "27414316", 35.1801, 128.1078);

        mockMvc.perform(post("/map/places/upload")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "kakaoPlaceId", "27414316",
                                "name", "이미지 포함 장소",
                                "address", "경상남도 진주시 이미지로 1",
                                "imageUrl", "https://example.com/images/place-upload.jpg",
                                "coordinateToken", coordinateToken
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("이미지 포함 장소"))
                .andExpect(jsonPath("$.address").value("경상남도 진주시 이미지로 1"));

        MapPlace saved = mapPlaceRepository.findByKakaoPlaceId("27414316").orElseThrow();
        assertEquals("https://example.com/images/place-upload.jpg", saved.getImageUrl());
    }

    @Test
    void getPlaceReturnsNotFoundWhenPlaceDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("reader03");

        mockMvc.perform(get("/place/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
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

        mockMvc.perform(get("/place/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1802")
                        .param("longitude", "128.1072")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v1"))
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

        mockMvc.perform(get("/place/recommendations")
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
    void recommendPlacesFallsBackToPopularNearbyPlacesWhenUserHasNoSignals() throws Exception {
        String accessToken = signupAndLogin("reader05");

        MapPlace popularPlace = createMapPlace("인기 장소", "경상남도 진주시 남강로 10", 35.1803, 128.1079, 4L);
        MapPlace normalPlace = createMapPlace("일반 장소", "경상남도 진주시 남강로 11", 35.1816, 128.1082, 1L);

        createMapImage(popularPlace, 20L, "인기 사진 1");
        createMapImage(popularPlace, 15L, "인기 사진 2");
        createMapImage(popularPlace, 10L, "인기 사진 3");
        createMapImage(popularPlace, 6L, "인기 사진 4");
        createMapImage(normalPlace, 0L, "일반 사진 1");

        mockMvc.perform(get("/place/recommendations")
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
    void recommendPlacesRecordsExposureLogs() throws Exception {
        String accessToken = signupAndLogin("reader10");

        MapPlace firstPlace = createMapPlace("노출 장소 A", "경상남도 진주시 본성동 1", 35.1802, 128.1078, 1L);
        MapPlace secondPlace = createMapPlace("노출 장소 B", "경상남도 진주시 본성동 2", 35.1804, 128.1080, 1L);

        createMapImage(firstPlace, 4L, "노출 사진 A");
        createMapImage(secondPlace, 3L, "노출 사진 B");

        mockMvc.perform(get("/place/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1801")
                        .param("longitude", "128.1078")
                        .param("limit", "2")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedCount").value(2));

        List<PlaceRecommendationExposure> exposures = placeRecommendationExposureRepository.findAll().stream()
                .sorted(Comparator.comparing(PlaceRecommendationExposure::getRanking))
                .toList();

        assertEquals(2, exposures.size());
        assertEquals(1, exposures.get(0).getRanking());
        assertEquals(2, exposures.get(1).getRanking());
        assertNotNull(exposures.get(0).getCreatedAt());
        assertEquals(35.1801d, exposures.get(0).getRequestLatitude());
        assertEquals(128.1078d, exposures.get(0).getRequestLongitude());

        PlaceRecommendationSnapshot firstSnapshot = placeRecommendationSnapshotRepository.findById(firstPlace.getId())
                .orElseThrow();
        PlaceRecommendationSnapshot secondSnapshot = placeRecommendationSnapshotRepository.findById(secondPlace.getId())
                .orElseThrow();
        assertEquals(1L, firstSnapshot.getExposureCount());
        assertEquals(1L, secondSnapshot.getExposureCount());
    }

    @Test
    void recommendPlacesAppliesExplorationBonusForLowExposurePlace() throws Exception {
        String accessToken = signupAndLogin("reader11");

        MapPlace lowExposurePlace = createMapPlace("저노출 장소", "경상남도 진주시 신안동 1", 35.1803, 128.1079, 1L);
        MapPlace highExposurePlace = createMapPlace("고노출 장소", "경상남도 진주시 신안동 2", 35.1803, 128.1079, 1L);

        createMapImage(lowExposurePlace, 5L, "저노출 사진");
        createMapImage(highExposurePlace, 5L, "고노출 사진");
        createExposureLogs(highExposurePlace.getId(), 30, 35.1801, 128.1078);

        mockMvc.perform(get("/place/recommendations")
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

        mockMvc.perform(post("/place/recommendations/click")
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

        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(clickedPlace.getId())
                .orElseThrow();
        assertEquals(1L, snapshot.getClickCount());
        assertEquals(0L, snapshot.getExposureCount());
    }

    @Test
    void createBookmarkRecordsRecommendationBookmarkConversion() throws Exception {
        String accessToken = signupAndLogin("reader15");
        MapPlace mapPlace = createMapPlace("북마크 전환 장소", "경상남도 진주시 전환로 1", 35.1803, 128.1079, 1L);

        mockMvc.perform(get("/place/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("latitude", "35.1803")
                        .param("longitude", "128.1079")
                        .param("limit", "1")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationVersion").value("place-rec-v1"));

        mockMvc.perform(post("/place/recommendations/click")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "placeId", mapPlace.getId(),
                                "recommendationVersion", "place-rec-v1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/bookmarks")
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

        List<PlaceRecommendationExposure> exposures = placeRecommendationExposureRepository.findAll();
        assertEquals(1, exposures.size());
        assertEquals("place-rec-v1", exposures.get(0).getRecommendationVersion());

        List<PlaceRecommendationClick> clicks = placeRecommendationClickRepository.findAll();
        assertEquals(1, clicks.size());
        assertEquals("place-rec-v1", clicks.get(0).getRecommendationVersion());
    }

    @Test
    void likeRecordsRecommendationLikeConversion() throws Exception {
        String accessToken = signupAndLogin("reader16");
        MapPlace mapPlace = createMapPlace("좋아요 전환 장소", "경상남도 진주시 전환로 2", 35.1803, 128.1079, 1L);
        MapImage mapImage = createMapImage(mapPlace, 0L, "좋아요 전환 사진");

        mockMvc.perform(post("/place/recommendations/click")
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

        mockMvc.perform(post("/place/recommendations/click")
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

        mockMvc.perform(get("/place/recommendations")
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

        mockMvc.perform(get("/place/recommendations")
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

        mockMvc.perform(get("/place/recommendations")
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

        mockMvc.perform(post("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("placeId", mapPlace.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()));

        PlaceRecommendationSnapshot createdSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(1L, createdSnapshot.getBookmarkCount());
        assertEquals(0L, createdSnapshot.getTotalLikeCount());

        mockMvc.perform(delete("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("placeId", mapPlace.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()));

        PlaceRecommendationSnapshot removedSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(0L, removedSnapshot.getBookmarkCount());
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

        mockMvc.perform(get("/place/recommendations")
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
        MvcResult coordinateResult = mockMvc.perform(post("/map/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "baseLatitude", latitude,
                                "baseLongitude", longitude,
                                "kakaoPlaceId", kakaoPlaceId
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(coordinateResult.getResponse().getContentAsString())
                .get("coordinateToken")
                .textValue();
    }

    private MapPlace createMapPlace(String name, String address) {
        return createMapPlace(name, address, 35.1801, 128.1078, 0L);
    }

    private MapPlace createMapPlace(String name, String address, double latitude, double longitude, long photoCount) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .userId(1L)
                .registrant("placeOwner")
                .photoCount(photoCount)
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

}
