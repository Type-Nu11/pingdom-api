package com.typenull.pingdom.place;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.MapBookmark;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
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

    @org.springframework.boot.test.mock.mockito.MockBean
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
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

}
