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

/**
 * 메모리 제한 저장소와 MockMvc로 인증·추천 클릭의 횟수, 최소 간격, 저장소 장애 HTTP 계약을 검증한다.
 */
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

        /**
         * UTC 시스템 시각을 쓰는 메모리 제한 저장소를 실제 저장소보다 우선하는 테스트 빈으로 제공한다.
         */
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

    /**
     * 메모리 제한 상태와 추천·게시물·인증 관련 DB 데이터를 초기화해 요청 횟수 누적을 시나리오 안으로 한정한다.
     */
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

    /**
     * 동일 이메일 가입이 생성·중복 충돌을 거친 뒤 세 번째 요청에서 429로 제한되는지 확인한다.
     */
    @Test
    void signupEmailLimit() throws Exception {
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

    /**
     * 같은 사용자명의 비밀번호 오류 두 번 후 세 번째 로그인 시도가 429인지 확인한다.
     */
    @Test
    void loginUsernameLimit() throws Exception {
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

    /**
     * 같은 잘못된 갱신 쿠키의 인증 실패도 횟수에 포함되어 세 번째 요청이 429인지 확인한다.
     */
    @Test
    void refreshTokenLimit() throws Exception {
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

    /**
     * 첫 인증 메일 재발급 성공 직후 반복 요청이 최소 간격 제한으로 거절되는지 확인한다.
     */
    @Test
    void emailResendCooldown() throws Exception {
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

    /**
     * 같은 이메일의 잘못된 인증 코드 두 번 후 세 번째 시도가 429인지 확인한다.
     */
    @Test
    void emailVerificationLimit() throws Exception {
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

    /**
     * 비밀번호 재설정 요청 직후의 반복 요청이 최소 간격 제한으로 거절되는지 확인한다.
     */
    @Test
    void passwordResetCooldown() throws Exception {
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

    /**
     * 서로 다른 장소·요청 ID라도 같은 사용자의 세 번째 추천 클릭은 횟수 제한에 걸리는지 확인한다.
     */
    @Test
    void recommendationUserLimit() throws Exception {
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

    /**
     * 장소를 바꿔도 추천 클릭 requestId를 재사용하면 두 번째 요청을 429로 거절하는지 확인한다.
     */
    @Test
    void recommendationRequestReuse() throws Exception {
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

    /**
     * 제한 저장소 대역의 장애가 로그인에서 503과 RATE_LIMIT_UNAVAILABLE로 노출되는지 확인한다.
     */
    @Test
    void rateLimitStoreUnavailable() throws Exception {
        rateLimitStore.simulateUnavailable();

        mockMvc.perform(post("/auth/login")
                        .with(remoteAddress("198.51.100.80"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("unavailableUser", "password123"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_UNAVAILABLE"));
    }

    /**
     * 공통 암호를 가진 사용자를 저장하고 flush해 인증 및 이메일 제한 시나리오에 사용한다.
     */
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

    /**
     * 로그인 횟수 제한을 소비하지 않고 사용자 접근 토큰을 직접 발급한다.
     */
    private String accessToken(User user) {
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }

    /**
     * 추천 클릭의 대상 존재 검증을 통과할 장소를 저장한다.
     */
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

    /**
     * 소유자·장소·S3 키와 초기 좋아요 0을 가진 업로드 fixture를 저장한다.
     */
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

    /**
     * 실제 JPEG 바이트를 file 필드의 multipart 요청 파일로 감싼다.
     */
    private MockMultipartFile imageFile(String filename) throws Exception {
        return new MockMultipartFile("file", filename, "image/jpeg", validJpegBytes());
    }

    /**
     * 파일 서명 검증을 통과하도록 2×2 RGB 이미지를 JPEG로 인코딩한다.
     */
    private byte[] validJpegBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    /**
     * 요청의 원격 주소를 명시해 IP별 제한 키를 시나리오마다 구분한다.
     */
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

        /**
         * 기간 만료와 쿨다운을 판단할 테스트 시계를 주입한다.
         */
        private TestRateLimitStore(Clock clock) {
            this.clock = clock;
        }

        /**
         * 메모리 대역에서 쿨다운과 기간별 횟수를 모두 검사한 뒤 횟수·다음 허용 시각을 갱신한다. Redis 원자성이나 동시 요청 안전성을 검증하는 구현은 아니다.
         */
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

        /**
         * 횟수·쿨다운·장애 플래그를 모두 초기화한다.
         */
        private void clear() {
            windows.clear();
            cooldowns.clear();
            unavailable = false;
        }

        /**
         * 후속 획득 호출이 저장소 장애 예외를 내도록 대역 상태를 바꾼다.
         */
        private void simulateUnavailable() {
            unavailable = true;
        }

        /**
         * 기간이 없거나 현재 시각이 만료 시각 이상이면 횟수 0의 새 기간을 반환하고 그 외에는 기존 상태를 쓴다.
         */
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

            /**
             * 요청 횟수와 기간 만료 시각을 묶어 메모리 제한 상태를 구성한다.
             */
            private WindowState(int count, Instant expiresAt) {
                this.count = count;
                this.expiresAt = expiresAt;
            }
        }

        private record CooldownState(Instant nextAllowedAt) {
        }
    }
}
