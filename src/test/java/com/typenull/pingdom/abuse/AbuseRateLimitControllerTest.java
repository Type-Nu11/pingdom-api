package com.typenull.pingdom.abuse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.shared.ratelimit.InMemoryRateLimitStore;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.storage.s3.S3ObjectStorage;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "abuse.rate-limit.login-username.limit=2",
        "abuse.rate-limit.token-refresh-token.limit=2",
        "abuse.rate-limit.email-resend.minimum-interval=PT1M",
        "abuse.rate-limit.report-user.limit=1",
        "abuse.rate-limit.map-image-like-user.limit=1",
        "abuse.rate-limit.recommendation-click-user.limit=1",
        "abuse.rate-limit.image-upload-user.limit=1"
})
@AutoConfigureMockMvc
class AbuseRateLimitControllerTest {

    @MockBean
    private S3ObjectStorage s3ObjectStorage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private InMemoryRateLimitStore rateLimitStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private UserSanctionHistoryRepository userSanctionHistoryRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private MapImageLikeRepository mapImageLikeRepository;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private PlaceRecommendationClickRepository placeRecommendationClickRepository;

    @Autowired
    private PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Autowired
    private PlaceRecommendationExposureRepository placeRecommendationExposureRepository;

    @Autowired
    private PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;

    @Autowired
    private PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Autowired
    private PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        rateLimitStore.clear();
        placeRecommendationVersionSnapshotRepository.deleteAllInBatch();
        placeRecommendationConversionRepository.deleteAllInBatch();
        placeRecommendationClickRepository.deleteAllInBatch();
        placeRecommendationExposureRepository.deleteAllInBatch();
        placeRecommendationFeatureLogRepository.deleteAllInBatch();
        placeRecommendationSnapshotRepository.deleteAllInBatch();
        postReportRepository.deleteAllInBatch();
        notificationsRepository.deleteAllInBatch();
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
        oAuthAccountRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void loginReturnsTooManyRequestsWhenUsernameLimitExceeded() throws Exception {
        createUser("limitedLoginUser");
        LoginRequest request = new LoginRequest("limitedLoginUser", "wrongpass");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                            .with(remoteAddress("198.51.100.10"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .with(remoteAddress("198.51.100.10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void tokenRefreshReturnsTooManyRequestsWhenTokenLimitExceeded() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/token/refresh")
                            .with(remoteAddress("198.51.100.20"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/token/refresh")
                        .with(remoteAddress("198.51.100.20"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void emailResendReturnsTooManyRequestsWhenMinimumIntervalExceeded() throws Exception {
        User user = createUser("limitedEmailUser");
        user.issueEmailVerification("123456", LocalDateTime.now().plusMinutes(10));
        userRepository.saveAndFlush(user);
        EmailResendRequest request = new EmailResendRequest(user.getEmail());

        mockMvc.perform(post("/auth/email/resend")
                        .with(remoteAddress("198.51.100.30"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/email/resend")
                        .with(remoteAddress("198.51.100.30"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void reportReturnsTooManyRequestsWhenUserLimitExceeded() throws Exception {
        User user = createUser("limitedReportUser");
        String accessToken = accessToken(user);
        MapImage firstImage = createMapImage(100L, null);
        MapImage secondImage = createMapImage(101L, null);

        mockMvc.perform(post("/map/post/{id}/report", firstImage.getId())
                        .with(remoteAddress("198.51.100.40"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "첫 번째 신고"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/post/{id}/report", secondImage.getId())
                        .with(remoteAddress("198.51.100.40"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "두 번째 신고"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void likeReturnsTooManyRequestsWhenUserLimitExceeded() throws Exception {
        User user = createUser("limitedLikeUser");
        String accessToken = accessToken(user);
        MapImage firstImage = createMapImage(200L, null);
        MapImage secondImage = createMapImage(201L, null);

        mockMvc.perform(post("/map/like")
                        .with(remoteAddress("198.51.100.50"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mapImageId", firstImage.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/map/like")
                        .with(remoteAddress("198.51.100.50"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("mapImageId", secondImage.getId()))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void recommendationClickReturnsTooManyRequestsWhenUserLimitExceeded() throws Exception {
        User user = createUser("limitedClickUser");
        String accessToken = accessToken(user);
        MapPlace place = createMapPlace("클릭 제한 장소");

        mockMvc.perform(post("/place/recommendations/click")
                        .with(remoteAddress("198.51.100.60"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", place.getId(),
                                "recommendationVersion", "place-rec-v1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/place/recommendations/click")
                        .with(remoteAddress("198.51.100.60"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", place.getId(),
                                "recommendationVersion", "place-rec-v1"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void imageUploadReturnsTooManyRequestsWhenUserLimitExceeded() throws Exception {
        given(s3ObjectStorage.put(any(), eq("map")))
                .willReturn(
                        new S3ObjectStorage.S3PutResult("map/first.jpg", "https://example.com/first.jpg"),
                        new S3ObjectStorage.S3PutResult("map/second.jpg", "https://example.com/second.jpg")
                );
        User user = createUser("limitedUploadUser");
        String accessToken = accessToken(user);
        MapPlace firstPlace = createMapPlace("첫 번째 업로드 장소");
        MapPlace secondPlace = createMapPlace("두 번째 업로드 장소");

        mockMvc.perform(multipart("/map/post/create")
                        .file(imageFile("first.jpg"))
                        .with(remoteAddress("198.51.100.70"))
                        .param("title", "첫 번째 업로드")
                        .param("placeId", firstPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/map/post/create")
                        .file(imageFile("second.jpg"))
                        .with(remoteAddress("198.51.100.70"))
                        .param("title", "두 번째 업로드")
                        .param("placeId", secondPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private User createUser(String username) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
    }

    private String accessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }

    private MapPlace createMapPlace(String name) {
        return mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name(name)
                .address("경상남도 진주시 제한로 1")
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(1L)
                .registrant("abuseTester")
                .build());
    }

    private MapImage createMapImage(Long ownerId, MapPlace mapPlace) {
        return mapImageRepository.saveAndFlush(MapImage.builder()
                .imageUrl("https://example.com/image-%d.jpg".formatted(ownerId))
                .s3Key("map/image-%d.jpg".formatted(ownerId))
                .title("제한 테스트 이미지")
                .description("제한 테스트 설명")
                .userId(ownerId)
                .username("owner" + ownerId)
                .mapPlace(mapPlace)
                .likeCount(0)
                .build());
    }

    private MockMultipartFile imageFile(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", "image-bytes".getBytes());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }
}
