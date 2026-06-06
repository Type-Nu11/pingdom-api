package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.MapBookmark;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.PlaceRecommendationExposure;
import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMapPlaceControllerTest {

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
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;

    @Autowired
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Autowired
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @BeforeEach
    void setUp() {
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
    void listPlacesReturnsRegisteredPlaces() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .address("경상남도 진주시 남강로 626")
                .latitude(35.1894)
                .longitude(128.0789)
                .userId(11L)
                .registrant("placeRegistrar")
                .build());

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].name").value("진주성"))
                .andExpect(jsonPath("$.places[0].address").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listPlacesRejectsMostLikedSort() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("sortParam", SortParam.MOST_LIKED.name()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PLACE_SORT_PARAM"));
    }

    @Test
    void getPlaceReturnsPlaceAndLinkedPosts() throws Exception {
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

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("남강")
                .address("경상남도 진주시 남강변")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(placeOwner.getId())
                .registrant(placeOwner.getUsername())
                .build());

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
                .andExpect(jsonPath("$.username").value("placeOwner"))
                .andExpect(jsonPath("$.sortParam").value(SortParam.LATEST.name()))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.posts[0].title").value("남강 야경"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(7))
                .andExpect(jsonPath("$.posts[0].username").value("placeOwner"));
    }

    @Test
    void getPlaceSortsPostsByMostLiked() throws Exception {
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

    @Test
    void getPlaceReturnsNotFoundWhenPlaceDoesNotExist() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void listRecommendationMetricsReturnsSortedCtrMetrics() throws Exception {
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

    @Test
    void listRecommendationMetricsSortsByTotalConversionRate() throws Exception {
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

    @Test
    void resyncRecommendationSnapshotsRebuildsCurrentPlacesAndRemovesOrphans() throws Exception {
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
                .build());
        placeRecommendationExposureRepository.save(PlaceRecommendationExposure.builder()
                .placeId(mapPlace.getId())
                .userId(301L)
                .requestLatitude(35.1801)
                .requestLongitude(128.1078)
                .ranking(2)
                .build());
        placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(mapPlace.getId())
                .userId(400L)
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(4001L)
                .placeId(mapPlace.getId())
                .userId(401L)
                .conversionType(PlaceRecommendationConversionType.BOOKMARK)
                .build());
        placeRecommendationConversionRepository.save(PlaceRecommendationConversion.builder()
                .placeRecommendationClickId(4002L)
                .placeId(mapPlace.getId())
                .userId(402L)
                .conversionType(PlaceRecommendationConversionType.LIKE)
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

        mockMvc.perform(post("/admin/places/recommendation-snapshots/resync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(1))
                .andExpect(jsonPath("$.synchronizedSnapshotCount").value(1))
                .andExpect(jsonPath("$.deletedSnapshotCount").value(1))
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
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(User.builder()
                .username("adminPlaceTester")
                .email("admin-place@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());

        LoginRequest loginRequest = new LoginRequest("adminPlaceTester", "password123");
        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
