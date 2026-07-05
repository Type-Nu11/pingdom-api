package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret"
})
@AutoConfigureMockMvc
class MapPostUploadControllerTest {

    @MockBean
    private S3ObjectStorage s3ObjectStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void uploadPostStoresTitleDescriptionAndFileMetadata() throws Exception {
        givenSuccessfulImageUpload(
                "map/test-key.jpg",
                "https://example.com/test-key.jpg",
                "map/thumbnails/test-key-thumbnail.jpg",
                "https://example.com/test-key-thumbnail.jpg"
        );

        String accessToken = signupAndLogin("writer01");
        MapPlace mapPlace = createMapPlace();
        MockMultipartFile file = imageFile("post.jpg");

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "새 게시글 제목")
                        .param("description", "게시글 부가 설명")
                        .param("placeId", mapPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글을 저장했습니다."));

        MapImage saved = mapImageRepository.findAll().get(0);
        assertEquals("새 게시글 제목", saved.getTitle());
        assertEquals("게시글 부가 설명", saved.getDescription());
        assertEquals("https://example.com/test-key.jpg", saved.getImageUrl());
        assertEquals("map/test-key.jpg", saved.getS3Key());
        assertEquals("https://example.com/test-key-thumbnail.jpg", saved.getThumbnailUrl());
        assertEquals("map/thumbnails/test-key-thumbnail.jpg", saved.getThumbnailS3Key());
        assertNotNull(saved.getUserId());
        assertEquals(mapPlace.getId(), saved.getMapPlace().getId());
    }

    @Test
    void uploadPostLegacyAliasStillWorks() throws Exception {
        givenSuccessfulImageUpload(
                "map/legacy-key.jpg",
                "https://example.com/legacy-key.jpg",
                "map/thumbnails/legacy-key-thumbnail.jpg",
                "https://example.com/legacy-key-thumbnail.jpg"
        );

        String accessToken = signupAndLogin("writer-legacy-upload");
        MapPlace mapPlace = createMapPlace();

        mockMvc.perform(multipart("/map/post/create")
                        .file(imageFile("legacy-post.jpg"))
                        .param("title", "레거시 업로드")
                        .param("placeId", mapPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글을 저장했습니다."));
    }

    @Test
    void uploadPostStoresPlaceWhenKakaoPlaceIdIsProvided() throws Exception {
        givenSuccessfulImageUpload(
                "map/test-key-kakao.jpg",
                "https://example.com/test-key-kakao.jpg",
                "map/thumbnails/test-key-kakao-thumbnail.jpg",
                "https://example.com/test-key-kakao-thumbnail.jpg"
        );

        String accessToken = signupAndLogin("writer-kakao-01");
        MapPlace mapPlace = createMapPlaceWithKakaoPlaceId("27414316");
        MockMultipartFile file = imageFile("post.jpg");

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "카카오 장소 업로드")
                        .param("kakaoPlaceId", "27414316")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글을 저장했습니다."));

        MapImage saved = mapImageRepository.findAll().get(0);
        assertEquals(mapPlace.getId(), saved.getMapPlace().getId());
    }

    @Test
    void uploadPostCreatesPlaceFromCoordinateTokenWhenPlaceReferenceIsMissing() throws Exception {
        givenSuccessfulImageUpload(
                "map/test-key-pin.jpg",
                "https://example.com/test-key-pin.jpg",
                "map/thumbnails/test-key-pin-thumbnail.jpg",
                "https://example.com/test-key-pin-thumbnail.jpg"
        );

        String accessToken = signupAndLogin("writer-pin-01");
        String coordinateToken = createCoordinateToken(accessToken, null, 35.1804, 128.1081);
        MockMultipartFile file = imageFile("post.jpg");

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "핀 좌표 게시글 업로드")
                        .param("description", "좌표 기반 장소 생성 후 게시글 저장")
                        .param("placeName", "핀 좌표 생성 장소")
                        .param("address", "경상남도 진주시 핀좌표로 10")
                        .param("category", "풍경")
                        .param("coordinateToken", coordinateToken)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글을 저장했습니다."))
                .andExpect(jsonPath("$.placeId").exists())
                .andExpect(jsonPath("$.postId").exists());

        assertEquals(1L, mapPlaceRepository.count());
        MapPlace savedPlace = mapPlaceRepository.findAll().get(0);
        assertEquals("핀 좌표 생성 장소", savedPlace.getName());
        assertEquals("경상남도 진주시 핀좌표로 10", savedPlace.getAddress());
        assertEquals("풍경", savedPlace.getCategory());
        assertEquals(1L, mapImageRepository.count());
        MapImage savedImage = mapImageRepository.findAll().get(0);
        assertEquals(savedPlace.getId(), savedImage.getMapPlace().getId());
    }

    @Test
    void uploadPostFailsWhenKakaoPlaceIdIsUnknown() throws Exception {
        String accessToken = signupAndLogin("writer-kakao-02");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "카카오 장소 업로드")
                        .param("kakaoPlaceId", "unknown-place-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLACE_NOT_FOUND"));
    }

    @Test
    void uploadPostFailsWhenKakaoPlaceIdAndPlaceIdAreMissing() throws Exception {
        String accessToken = signupAndLogin("writer-kakao-03");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

                mockMvc.perform(multipart("/map/posts")
                                .file(file)
                                .param("title", "카카오 장소 업로드")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errors.validPlace").value("장소 ID, 카카오 장소 ID 또는 좌표 기반 장소 정보는 필수입니다."));
    }

    @Test
    void uploadPostFailsWhenTitleIsBlank() throws Exception {
        String accessToken = signupAndLogin("writer02");
        MapPlace mapPlace = createMapPlace();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "   ")
                        .param("placeId", mapPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("제목은 필수입니다."));
    }

    @Test
    void uploadPostFailsWhenTitleExceedsColumnLimit() throws Exception {
        String accessToken = signupAndLogin("writer03");
        MapPlace mapPlace = createMapPlace();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "a".repeat(101))
                        .param("placeId", mapPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("제목은 100자 이하여야 합니다."));
    }

    @Test
    void uploadPostFailsWhenDescriptionExceedsColumnLimit() throws Exception {
        String accessToken = signupAndLogin("writer04");
        MapPlace mapPlace = createMapPlace();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "post.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "정상 제목")
                        .param("description", "a".repeat(1001))
                        .param("placeId", mapPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").value("부가 설명은 1000자 이하여야 합니다."));
    }

    @Test
    void deletePostRemovesDatabaseRecordAndCreatesS3DeleteOutboxEvent() throws Exception {
        String accessToken = signupAndLogin("writer05");
        Long userId = userRepository.findByUsername("writer05").orElseThrow().getId();
        MapImage mapImage = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/delete-post.jpg")
                .s3Key("map/delete-post.jpg")
                .thumbnailUrl("https://example.com/delete-post-thumbnail.jpg")
                .thumbnailS3Key("map/thumbnails/delete-post-thumbnail.jpg")
                .title("삭제 테스트 제목")
                .description("삭제 테스트 설명")
                .userId(userId)
                .build());

        mockMvc.perform(delete("/map/posts/{id}", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("게시글을 삭제했습니다."));

        verify(s3ObjectStorage, never()).delete("map/delete-post.jpg");
        assertEquals(0L, mapImageRepository.count());
        assertS3DeleteOutboxEvent(mapImage.getId(), "map/delete-post.jpg", "MAP_IMAGE_DELETED");
        assertS3DeleteOutboxEvent(mapImage.getId(), "map/thumbnails/delete-post-thumbnail.jpg", "MAP_IMAGE_THUMBNAIL_DELETED");
    }

    @Test
    void deletePostLegacyAliasStillWorks() throws Exception {
        String accessToken = signupAndLogin("writer-legacy-delete");
        Long userId = userRepository.findByUsername("writer-legacy-delete").orElseThrow().getId();
        MapImage mapImage = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/legacy-delete-post.jpg")
                .s3Key("map/legacy-delete-post.jpg")
                .title("레거시 삭제 테스트")
                .userId(userId)
                .build());

        mockMvc.perform(delete("/map/post/{id}/delete", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("게시글을 삭제했습니다."));
    }

    @Test
    void uploadAndDeletePostRefreshRecommendationSnapshot() throws Exception {
        givenSuccessfulImageUpload(
                "map/snapshot-post.jpg",
                "https://example.com/snapshot-post.jpg",
                "map/thumbnails/snapshot-post-thumbnail.jpg",
                "https://example.com/snapshot-post-thumbnail.jpg"
        );

        String accessToken = signupAndLogin("writer06");
        MapPlace mapPlace = createMapPlace();
        MockMultipartFile file = imageFile("snapshot-post.jpg");

        mockMvc.perform(multipart("/map/posts")
                        .file(file)
                        .param("title", "snapshot 검증 게시글")
                        .param("description", "snapshot 생성 확인")
                        .param("placeId", mapPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("게시글을 저장했습니다."));

        PlaceRecommendationSnapshot uploadedSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(1L, uploadedSnapshot.getPhotoCount());
        assertEquals(0L, uploadedSnapshot.getBookmarkCount());
        assertEquals(0L, uploadedSnapshot.getTotalLikeCount());
        assertNotNull(uploadedSnapshot.getLatestPostCreatedAt());

        MapImage saved = mapImageRepository.findAll().get(0);

        mockMvc.perform(delete("/map/posts/{id}", saved.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("게시글을 삭제했습니다."));

        PlaceRecommendationSnapshot deletedSnapshot = placeRecommendationSnapshotRepository.findById(mapPlace.getId())
                .orElseThrow();
        assertEquals(0L, deletedSnapshot.getPhotoCount());
        assertEquals(0L, deletedSnapshot.getTotalLikeCount());
        assertEquals(0L, mapImageRepository.count());
        verify(s3ObjectStorage, never()).delete("map/snapshot-post.jpg");
        assertS3DeleteOutboxEvent(saved.getId(), "map/snapshot-post.jpg", "MAP_IMAGE_DELETED");
        assertS3DeleteOutboxEvent(saved.getId(), "map/thumbnails/snapshot-post-thumbnail.jpg", "MAP_IMAGE_THUMBNAIL_DELETED");
    }

    private void assertS3DeleteOutboxEvent(Long mapImageId, String s3Key, String reason) throws Exception {
        List<OutboxEvent> events = outboxEventRepository.findAll()
                .stream()
                .filter(event -> event.getEventType() == OutboxEventType.S3_OBJECT_DELETE_REQUESTED)
                .toList();
        for (OutboxEvent event : events) {
            if (s3Key.equals(objectMapper.readTree(event.getPayload()).get("s3Key").asText())) {
                assertEquals(OutboxEventType.S3_OBJECT_DELETE_REQUESTED, event.getEventType());
                assertEquals("MAP_IMAGE", event.getAggregateType());
                assertEquals(String.valueOf(mapImageId), event.getAggregateId());
                assertEquals(reason, objectMapper.readTree(event.getPayload()).get("reason").asText());
                return;
            }
        }
        fail("Expected S3 delete outbox event was not found. s3Key=" + s3Key);
    }

    private void givenSuccessfulImageUpload(String key, String url, String thumbnailKey, String thumbnailUrl) {
        given(s3ObjectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("map")))
                .willReturn(new S3ObjectStorage.S3PutResult(key, url));
        given(s3ObjectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("map/thumbnails")))
                .willReturn(new S3ObjectStorage.S3PutResult(thumbnailKey, thumbnailUrl));
    }

    private MockMultipartFile imageFile(String filename) throws Exception {
        return new MockMultipartFile("file", filename, "image/jpeg", validJpegBytes());
    }

    private byte[] validJpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    private String signupAndLogin(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                username,
                username + "@example.com",
                "password123",
                1998,
                null,
                "ko",
                "KR"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(username, "password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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

        MvcResult coordinateResult = mockMvc.perform(post("/map/places/coordinates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(coordinateResult.getResponse().getContentAsString())
                .get("coordinateToken")
                .textValue();
    }

    private MapPlace createMapPlace() {
        return mapPlaceRepository.save(MapPlace.builder()
                .name("테스트 장소")
                .address("경상남도 진주시 테스트로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(1L)
                .registrant("uploadTester")
                .build());
    }

    private MapPlace createMapPlaceWithKakaoPlaceId(String kakaoPlaceId) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name("카카오 장소")
                .address("경상남도 진주시 테스트로 2")
                .kakaoPlaceId(kakaoPlaceId)
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(1L)
                .registrant("uploadTester")
                .build());
    }
}
