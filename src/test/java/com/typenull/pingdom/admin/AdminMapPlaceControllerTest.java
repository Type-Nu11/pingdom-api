package com.typenull.pingdom.admin;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationClick;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationExposure;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.domain.recommendation.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrafficPolicyRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
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
    private AdminPlaceMergeHistoryRepository adminPlaceMergeHistoryRepository;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
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
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listPlacesReturnsRegisteredPlaces() throws Exception {
        String accessToken = createAdminAndLogin();
        mapPlaceRepository.save(MapPlace.builder()
                .name("진주성")
                .address("경상남도 진주시 남강로 626")
                .category("관광")
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
                .andExpect(jsonPath("$.places[0].category").value("관광"))
                .andExpect(jsonPath("$.places[0].categoryName").value("관광"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void listPlacesReturnsUncategorizedNameWhenCategoryIsMissing() throws Exception {
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
                .andExpect(jsonPath("$.places[0].categoryName").value("미분류"));
    }

    @Test
    void getPlaceReturnsUncategorizedNameWhenCategoryIsMissing() throws Exception {
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
                .andExpect(jsonPath("$.categoryName").value("미분류"));
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
    void listPlacesFiltersByKeywordAcrossAddressAndRegistrantUserId() throws Exception {
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

    @Test
    void getRecommendationExplanationReturnsFeatureLogsForAdmin() throws Exception {
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
                .andExpect(jsonPath("$.items[0].ranking").value(1));
    }

    @Test
    void getRecommendationExplanationReturnsNotFoundWhenMissing() throws Exception {
        String accessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/places/recommendations/{requestId}/explanation", "missing-request-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EXPLANATION_NOT_FOUND"));
    }

    @Test
    void listPlacesMatchesRegistrantUserIdExactlyWhenKeywordIsNumeric() throws Exception {
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
                .category("풍경")
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
                .andExpect(jsonPath("$.category").value("풍경"))
                .andExpect(jsonPath("$.categoryName").value("풍경"))
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
    void deletePlaceDeletesLinkedPosts() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("삭제 대상 장소")
                .address("경상남도 진주시 삭제로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(94L)
                .registrant("deleteOwner")
                .photoCount(2L)
                .build());

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
        assertFalse(mapImageRepository.existsById(firstPost.getId()));
        assertFalse(mapImageRepository.existsById(secondPost.getId()));
        assertEquals(0L, mapImageRepository.countByMapPlace_Id(mapPlace.getId()));
        assertEquals(3L, adminAuditLogRepository.count());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.PLACE_DELETED));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.PLACE_DELETED)
                .anyMatch(log -> log.getAfterState().contains("\"deletedPostCount\":2")));
    }

    @Test
    void updatePlaceCoordinatesUpdatesLatitudeLongitudeAndLocation() throws Exception {
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
        assertNotNull(updatedPlace.getLocation());
        assertEquals(128.1076, updatedPlace.getLocation().getX());
        assertEquals(35.1796, updatedPlace.getLocation().getY());
    }

    @Test
    void updatePlaceKakaoPlaceIdReconnectsPlace() throws Exception {
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

    @Test
    void updatePlaceKakaoPlaceIdRejectsDuplicateId() throws Exception {
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

    @Test
    void updateRecommendationTrafficOverridesTrafficPercentages() throws Exception {
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

    @Test
    void updateRecommendationTrafficRejectsInvalidTotal() throws Exception {
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

    @Test
    void updateRecommendationTrafficRejectsPartialPolicyUpdate() throws Exception {
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

    @Test
    void updateRecommendationTrafficRejectsDisabledPolicyWithoutFallbackVersion() throws Exception {
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

    @Test
    void updateRecommendationTrafficRejectsFallbackCycle() throws Exception {
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

    @Test
    void mergePlacesMovesReferencesAndDeletesSourcePlace() throws Exception {
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

    @Test
    void listMergeHistoriesAndRestoreMergeWork() throws Exception {
        String accessToken = createAdminAndLogin();

        MapPlace sourcePlace = mapPlaceRepository.save(MapPlace.builder()
                .name("복구 병합 장소")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .kakaoPlaceId("restore-source-id")
                .latitude(35.642738)
                .longitude(128.391626)
                .userId(30L)
                .registrant("sourceOwner")
                .photoCount(1L)
                .build());
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
    void listRecommendationMetricsSortsUpdatedAtWithNullSnapshotsLast() throws Exception {
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

    @Test
    void listRecommendationMetricsFiltersByRecommendationVersion() throws Exception {
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

    @Test
    void listRecommendationMetricsFiltersByRecentDays() throws Exception {
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

    @Test
    void listRecommendationMetricsCombinesDaysVersionSortAndPagination() throws Exception {
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

    @Test
    void listRecommendationMetricsPaginatesStableOrderForPeriodClickSort() throws Exception {
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

    @Test
    void compareRecommendationMetricsReturnsVersionSummaryAndDelta() throws Exception {
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

    @Test
    void resyncRecommendationSnapshotsSynchronizesPlaceSimilaritySnapshots() throws Exception {
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

    private String createAdminAndLogin() throws Exception {
        String username = "adminPlaceTester" + ADMIN_SEQUENCE.incrementAndGet();
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
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
