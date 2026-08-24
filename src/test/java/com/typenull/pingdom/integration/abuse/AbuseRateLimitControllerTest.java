package com.typenull.pingdom.integration.abuse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
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
import com.typenull.pingdom.shared.ratelimit.core.RateLimitCooldownRule;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitUnavailableException;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitWindowRule;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import com.typenull.pingdom.shared.support.S3ObjectStorage;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "abuse.rate-limit.signup-email.limit=2",
        "abuse.rate-limit.login-username.limit=2",
        "abuse.rate-limit.token-refresh-token.limit=2",
        "abuse.rate-limit.email-resend.minimum-interval=PT1M",
        "abuse.rate-limit.email-verify-email.limit=2",
        "abuse.rate-limit.password-reset-request.minimum-interval=PT1M",
        "abuse.rate-limit.report-user.limit=1",
        "abuse.rate-limit.map-image-like-user.limit=1",
        "abuse.rate-limit.recommendation-click-user.limit=2",
        "abuse.rate-limit.image-upload-user.limit=1"
})
@AutoConfigureMockMvc
class AbuseRateLimitControllerTest {

    @TestConfiguration
    static class TestRateLimitConfig {

        @Bean
        @Primary
        TestRateLimitStore testRateLimitStore() {
            return new TestRateLimitStore(Clock.systemUTC());
        }
    }

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
    private TestRateLimitStore rateLimitStore;

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

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

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
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void signupReturnsTooManyRequestsWhenEmailLimitExceeded() throws Exception {
        SignupRequest request = new SignupRequest(
                "limitedSignupUser",
                "limited-signup@example.com",
                "password123",
                1998,
                null,
                "ko",
                "KR"
        );

        mockMvc.perform(post("/auth/signup")
                        .with(remoteAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/signup")
                        .with(remoteAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/auth/signup")
                        .with(remoteAddress("198.51.100.11"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
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
        Cookie refreshTokenCookie = new Cookie("PINGDOM_REFRESH_TOKEN", "invalid-refresh-token");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/token/refresh")
                            .with(remoteAddress("198.51.100.20"))
                            .cookie(refreshTokenCookie))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/token/refresh")
                        .with(remoteAddress("198.51.100.20"))
                        .cookie(refreshTokenCookie))
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
    void emailVerifyReturnsTooManyRequestsWhenEmailLimitExceeded() throws Exception {
        User user = createUser("limitedEmailVerifyUser");
        user.issueEmailVerification("123456", LocalDateTime.now().plusMinutes(10));
        userRepository.saveAndFlush(user);
        EmailVerifyRequest request = new EmailVerifyRequest(user.getEmail(), "000000");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/email/verify")
                            .with(remoteAddress("198.51.100.32"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/auth/email/verify")
                        .with(remoteAddress("198.51.100.32"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void passwordResetRequestReturnsTooManyRequestsWhenMinimumIntervalExceeded() throws Exception {
        User user = createUser("limitedPasswordResetUser");
        PasswordResetRequest request = new PasswordResetRequest(user.getEmail());

        mockMvc.perform(post("/auth/password-reset/request")
                        .with(remoteAddress("198.51.100.31"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/password-reset/request")
                        .with(remoteAddress("198.51.100.31"))
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

        mockMvc.perform(post("/map/posts/{id}/report", firstImage.getId())
                        .with(remoteAddress("198.51.100.40"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "첫 번째 신고"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/posts/{id}/report", secondImage.getId())
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
        MapPlace firstPlace = createMapPlace("첫 번째 클릭 제한 장소");
        MapPlace secondPlace = createMapPlace("두 번째 클릭 제한 장소");
        MapPlace thirdPlace = createMapPlace("세 번째 클릭 제한 장소");

        mockMvc.perform(post("/places/recommendations/click")
                        .with(remoteAddress("198.51.100.60"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", firstPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "recommendation-limit-request-1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/places/recommendations/click")
                        .with(remoteAddress("198.51.100.60"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", secondPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "recommendation-limit-request-2"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/places/recommendations/click")
                        .with(remoteAddress("198.51.100.60"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", thirdPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "recommendation-limit-request-3"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void recommendationClickReturnsTooManyRequestsWhenRequestIdIsReused() throws Exception {
        User user = createUser("reusedClickUser");
        String accessToken = accessToken(user);
        MapPlace firstPlace = createMapPlace("첫 번째 추천 클릭 장소");
        MapPlace secondPlace = createMapPlace("두 번째 추천 클릭 장소");

        mockMvc.perform(post("/places/recommendations/click")
                        .with(remoteAddress("198.51.100.61"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", firstPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "recommendation-request-1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/places/recommendations/click")
                        .with(remoteAddress("198.51.100.61"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "placeId", secondPlace.getId(),
                                "recommendationVersion", "place-rec-v1",
                                "requestId", "recommendation-request-1"
                        ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void imageUploadReturnsTooManyRequestsWhenUserLimitExceeded() throws Exception {
        given(s3ObjectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("map")))
                .willReturn(new S3ObjectStorage.S3PutResult("map/first.jpg", "https://example.com/first.jpg"));
        given(s3ObjectStorage.put(any(byte[].class), anyString(), eq("image/jpeg"), eq("map/thumbnails")))
                .willReturn(new S3ObjectStorage.S3PutResult(
                        "map/thumbnails/first-thumbnail.jpg",
                        "https://example.com/first-thumbnail.jpg"
                ));
        User user = createUser("limitedUploadUser");
        String accessToken = accessToken(user);
        MapPlace firstPlace = createMapPlace("첫 번째 업로드 장소");
        MapPlace secondPlace = createMapPlace("두 번째 업로드 장소");

        mockMvc.perform(multipart("/map/posts")
                        .file(imageFile("first.jpg"))
                        .with(remoteAddress("198.51.100.70"))
                        .param("title", "첫 번째 업로드")
                        .param("placeId", firstPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/map/posts")
                        .file(imageFile("second.jpg"))
                        .with(remoteAddress("198.51.100.70"))
                        .param("title", "두 번째 업로드")
                        .param("placeId", secondPlace.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void loginReturnsServiceUnavailableWhenRateLimitStoreIsUnavailable() throws Exception {
        rateLimitStore.simulateUnavailable();

        mockMvc.perform(post("/auth/login")
                        .with(remoteAddress("198.51.100.80"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("unavailableUser", "password123"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_UNAVAILABLE"));
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

    private MockMultipartFile imageFile(String filename) throws Exception {
        return new MockMultipartFile("file", filename, "image/jpeg", validJpegBytes());
    }

    private byte[] validJpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    static class TestRateLimitStore implements RateLimitStore {

        private final Clock clock;
        private final Map<String, WindowState> windows = new ConcurrentHashMap<>();
        private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();
        private boolean unavailable;

        private TestRateLimitStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void acquire(
                String message,
                Collection<RateLimitWindowRule> windowRules,
                Collection<RateLimitCooldownRule> cooldownRules
        ) {
            if (unavailable) {
                throw new RateLimitUnavailableException(new IllegalStateException("redis unavailable"));
            }
            Instant now = Instant.now(clock);
            for (RateLimitCooldownRule rule : cooldownRules) {
                CooldownState state = cooldowns.get(rule.key());
                if (state != null && now.isBefore(state.nextAllowedAt())) {
                    throw new RateLimitException(message);
                }
            }

            for (RateLimitWindowRule rule : windowRules) {
                WindowState state = activeWindowState(rule, now);
                if (state.count >= rule.limit()) {
                    throw new RateLimitException(message);
                }
            }

            for (RateLimitWindowRule rule : windowRules) {
                WindowState state = activeWindowState(rule, now);
                state.count++;
                windows.put(rule.key(), state);
            }

            for (RateLimitCooldownRule rule : cooldownRules) {
                cooldowns.put(rule.key(), new CooldownState(now.plus(rule.interval())));
            }
        }

        private void clear() {
            windows.clear();
            cooldowns.clear();
            unavailable = false;
        }

        private void simulateUnavailable() {
            unavailable = true;
        }

        private WindowState activeWindowState(RateLimitWindowRule rule, Instant now) {
            WindowState state = windows.get(rule.key());
            if (state == null || !now.isBefore(state.expiresAt)) {
                return new WindowState(0, now.plus(rule.window()));
            }
            return state;
        }

        private static final class WindowState {

            private int count;
            private final Instant expiresAt;

            private WindowState(int count, Instant expiresAt) {
                this.count = count;
                this.expiresAt = expiresAt;
            }
        }

        private record CooldownState(Instant nextAllowedAt) {
        }
    }
}
