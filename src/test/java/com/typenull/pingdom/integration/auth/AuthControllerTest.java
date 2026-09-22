package com.typenull.pingdom.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.application.service.withdrawal.WithdrawnUserPurgeService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.application.port.EmailSendResult;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.outbox.PasswordResetOutboxPayload;
import com.typenull.pingdom.notification.infrastructure.persistence.FcmDeviceTokenRepository;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 메일 발송과 요청 제한을 대역으로 격리하고 가입·로그인·인증·재설정·탈퇴의 API와 저장 상태를 검증한다.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "PINGDOM_REFRESH_TOKEN";

    @TestConfiguration
    static class TestEmailSenderConfig {

        /**
         * 외부 발송 없이 성공 결과를 주는 메일 대역을 등록해 인증·재설정의 DB 및 API 계약에 집중한다.
         */
        @Bean
        @Primary
        // 테스트용 메일 발송 대체 빈
        EmailSender emailSender() {
            return new EmailSender() {
                /**
                 * 인증 메일 공급자를 호출하지 않고 message ID 없는 성공 결과를 반환한다.
                 */
                @Override
                public EmailSendResult sendVerificationEmail(String recipientEmail, String verificationCode) {
                    return EmailSendResult.sent(null);
                }

                /**
                 * 비밀번호 재설정 메일을 실제 발송하지 않고 성공 결과를 반환한다.
                 */
                @Override
                public EmailSendResult sendPasswordResetEmail(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
                    return EmailSendResult.sent(null);
                }
            };
        }

        /**
         * 요청 제한을 소비하거나 거절하지 않는 대역으로 인증 업무 흐름을 격리한다.
         */
        @Bean
        @Primary
        RateLimitStore rateLimitStore() {
            return (message, windowRules, cooldownRules) -> {
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuthAccountRepository oAuthAccountRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private MapImageLikeRepository mapImageLikeRepository;

    @Autowired
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private NotificationsRepository notificationsRepository;

    @Autowired
    private FcmDeviceTokenRepository fcmDeviceTokenRepository;

    @Autowired
    private WithdrawnUserPurgeService withdrawnUserPurgeService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /**
     * 알림·기기 토큰·사용자 활동·콘텐츠·OAuth·outbox·재설정 토큰을 사용자보다 먼저 비운다.
     */
    @BeforeEach
    void setUp() {
        notificationsRepository.deleteAllInBatch();
        fcmDeviceTokenRepository.deleteAllInBatch();
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        oAuthAccountRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 제거된 /auth/google 별칭이 404인지 확인한다.
     */
    @Test
    void removedGoogleAlias() throws Exception {
        mockMvc.perform(get("/auth/google"))
                .andExpect(status().isNotFound());
    }

    /**
     * 회원 가입이 미인증 사용자와 인증 코드를 저장하고 메일 발송 요청 outbox를 남기는지 확인한다.
     */
    @Test
    void signupCreatesUser() throws Exception {
        SignupRequest request = new SignupRequest("tester01", "tester01@example.com", "password123", 1998, null, "ko", "KR");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("tester01"))
                .andExpect(jsonPath("$.email").value("tester01@example.com"));

        User user = userRepository.findByUsername("tester01").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(user.getEmailVerificationCode());
        org.junit.jupiter.api.Assertions.assertFalse(user.isEmailVerified());
        org.junit.jupiter.api.Assertions.assertEquals(
                OutboxEventType.EMAIL_VERIFICATION_REQUESTED,
                outboxEventRepository.findAll().getFirst().getEventType()
        );
    }

    /**
     * 올바른 로그인에서 접근 토큰은 본문에, 갱신 토큰은 /auth·HttpOnly·Secure·SameSite=Lax 쿠키에 전달되는지 확인한다.
     */
    @Test
    void loginTokenContract() throws Exception {
        SignupRequest signupRequest = new SignupRequest("loginuser", "loginuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("loginuser", "password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("PINGDOM_REFRESH_TOKEN="));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/auth"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Secure"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Lax"));
    }

    /**
     * 잘못된 비밀번호가 401과 공통 자격 증명 오류 메시지를 반환하는지 확인한다.
     */
    @Test
    void wrongPasswordLogin() throws Exception {
        SignupRequest signupRequest = new SignupRequest("failuser", "failuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("failuser", "wrongpass");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    /**
     * localhost:5173의 갱신 preflight에서 해당 origin과 credentials 허용 헤더를 확인한다.
     */
    @Test
    void refreshCors5173() throws Exception {
        mockMvc.perform(options("/auth/token/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    /**
     * localhost:5174의 갱신 preflight에서도 쿠키 사용을 허용하는지 확인한다.
     */
    @Test
    void refreshCors5174() throws Exception {
        mockMvc.perform(options("/auth/token/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    /**
     * 가입 후 발급된 코드로 인증하면 저장된 사용자의 이메일 인증 상태가 참인지 확인한다.
     */
    @Test
    void verifyEmail() throws Exception {
        SignupRequest signupRequest = new SignupRequest("emailuser", "emailuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        User issuedUser = userRepository.findByUsername("emailuser").orElseThrow();
        EmailVerifyRequest verifyRequest = new EmailVerifyRequest("emailuser@example.com", issuedUser.getEmailVerificationCode());

        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("emailuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(user.isEmailVerified());
    }

    /**
     * 만료 코드를 재발급하면 미래 만료 시각의 새 코드만 인증에 성공하고 이전 코드는 거절되는지 확인한다.
     */
    @Test
    void resendRotatesVerificationCode() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resenduser", "resenduser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        User user = userRepository.findByUsername("resenduser").orElseThrow();
        user.issueEmailVerification("TEMP-CODE", LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);

        EmailResendRequest resendRequest = new EmailResendRequest("resenduser@example.com");

        mockMvc.perform(post("/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isOk());

        User updatedUser = userRepository.findByUsername("resenduser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(updatedUser.isEmailVerified());
        org.junit.jupiter.api.Assertions.assertNotNull(updatedUser.getEmailVerificationCode());
        org.junit.jupiter.api.Assertions.assertNotEquals("TEMP-CODE", updatedUser.getEmailVerificationCode());
        org.junit.jupiter.api.Assertions.assertTrue(updatedUser.getEmailVerificationExpiresAt()
                .isAfter(LocalDateTime.now(Clock.systemUTC())));

        EmailVerifyRequest oldVerifyRequest = new EmailVerifyRequest("resenduser@example.com", "TEMP-CODE");
        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldVerifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_CODE"));

        EmailVerifyRequest newVerifyRequest = new EmailVerifyRequest("resenduser@example.com", updatedUser.getEmailVerificationCode());
        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newVerifyRequest)))
                .andExpect(status().isOk());
    }

    /**
     * 인증을 마친 사용자는 재발송 시 EMAIL_ALREADY_VERIFIED 충돌을 받는지 확인한다.
     */
    @Test
    void resendVerifiedEmail() throws Exception {
        SignupRequest signupRequest = new SignupRequest("verifieduser", "verifieduser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        User issuedUser = userRepository.findByUsername("verifieduser").orElseThrow();
        EmailVerifyRequest verifyRequest = new EmailVerifyRequest("verifieduser@example.com", issuedUser.getEmailVerificationCode());
        mockMvc.perform(post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyRequest)));

        EmailResendRequest resendRequest = new EmailResendRequest("verifieduser@example.com");
        mockMvc.perform(post("/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    /**
     * 제재 사용자에게 인증 메일 재발송을 USER_BANNED로 거절하는지 확인한다.
     */
    @Test
    void resendBannedUser() throws Exception {
        SignupRequest signupRequest = new SignupRequest("banneduser", "banneduser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        User user = userRepository.findByUsername("banneduser").orElseThrow();
        user.ban("테스트 밴", LocalDateTime.now());
        userRepository.saveAndFlush(user);

        EmailResendRequest resendRequest = new EmailResendRequest("banneduser@example.com");
        mockMvc.perform(post("/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));
    }

    /**
     * 없는 이메일의 인증 메일 재발송이 USER_NOT_FOUND인지 확인한다.
     */
    @Test
    void resendMissingUser() throws Exception {
        EmailResendRequest resendRequest = new EmailResendRequest("missing@example.com");

        mockMvc.perform(post("/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /**
     * 재설정 요청이 원문 토큰을 메일 outbox에 담고 토큰 저장소에는 원문과 다른 64자 해시를 저장하는지 확인한다.
     */
    @Test
    void resetTokenAndOutbox() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resetuser", "resetuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));
        outboxEventRepository.deleteAllInBatch();

        PasswordResetRequest request = new PasswordResetRequest("resetuser@example.com");

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PasswordResetOutboxPayload payload = passwordResetPayload();
        PasswordResetToken token = passwordResetTokenRepository.findAll().getFirst();
        org.junit.jupiter.api.Assertions.assertEquals("resetuser@example.com", payload.recipientEmail());
        org.junit.jupiter.api.Assertions.assertNotNull(payload.resetToken());
        org.junit.jupiter.api.Assertions.assertEquals(64, token.getTokenHash().length());
        org.junit.jupiter.api.Assertions.assertNotEquals(payload.resetToken(), token.getTokenHash());
    }

    /**
     * 소문자 이메일 요청으로 기존 대소문자 혼합 이메일을 찾아 원래 주소로 발송 payload를 만드는지 확인한다.
     */
    @Test
    void resetRequestEmailCase() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resetrequestcaseuser", "ResetRequestCase@Example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));
        outboxEventRepository.deleteAllInBatch();

        PasswordResetRequest request = new PasswordResetRequest("resetrequestcase@example.com");

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        PasswordResetOutboxPayload payload = passwordResetPayload();
        org.junit.jupiter.api.Assertions.assertEquals("ResetRequestCase@Example.com", payload.recipientEmail());
    }

    /**
     * 없는 이메일도 200을 반환하되 재설정 토큰과 outbox는 만들지 않는지 확인한다.
     */
    @Test
    void resetMissingEmail() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("missing-reset@example.com");

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(passwordResetTokenRepository.findAll().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(outboxEventRepository.findAll().isEmpty());
    }

    /**
     * 재설정 완료 후 기존 비밀번호와 갱신 토큰은 거절되고 새 비밀번호로 로그인되는지 확인한다.
     */
    @Test
    void resetPasswordAndRevokeRefresh() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resetconfirmuser", "resetconfirmuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("resetconfirmuser", "password123"))))
                .andExpect(status().isOk());
        String refreshToken = refreshTokenOf("resetconfirmuser");

        outboxEventRepository.deleteAllInBatch();
        requestPasswordReset("resetconfirmuser@example.com");
        PasswordResetOutboxPayload payload = passwordResetPayload();
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(
                payload.recipientEmail(),
                payload.resetToken(),
                "newPassword123",
                "newPassword123"
        );

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("resetconfirmuser", "password123"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/token/refresh")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("resetconfirmuser", "newPassword123"))))
                .andExpect(status().isOk());
    }

    /**
     * 확인 요청의 이메일 대소문자가 달라도 동일 사용자 토큰을 인정하는지 확인한다.
     */
    @Test
    void resetConfirmEmailCase() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resetcaseuser", "resetcaseuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));
        outboxEventRepository.deleteAllInBatch();
        requestPasswordReset("resetcaseuser@example.com");
        PasswordResetOutboxPayload payload = passwordResetPayload();
        PasswordResetConfirmRequest confirmRequest = new PasswordResetConfirmRequest(
                "RESETCASEUSER@EXAMPLE.COM",
                payload.resetToken(),
                "newPassword123",
                "newPassword123"
        );

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isNoContent());
    }

    /**
     * 이미 만료된 해시 토큰 fixture의 확인 요청이 EXPIRED_PASSWORD_RESET_TOKEN인지 확인한다.
     */
    @Test
    void expiredResetToken() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .username("expiredresetuser")
                .email("expiredresetuser@example.com")
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
        passwordResetTokenRepository.saveAndFlush(PasswordResetToken.create(
                user,
                passwordResetTokenHash("expired-reset-token"),
                LocalDateTime.now(Clock.systemUTC()).minusMinutes(1),
                LocalDateTime.now(Clock.systemUTC()).minusMinutes(31)
        ));
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                user.getEmail(),
                "expired-reset-token",
                "newPassword123",
                "newPassword123"
        );

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXPIRED_PASSWORD_RESET_TOKEN"));
    }

    /**
     * 같은 재설정 토큰으로 한 번 성공한 뒤 다시 확인하면 INVALID_PASSWORD_RESET_TOKEN인지 확인한다.
     */
    @Test
    void reusedResetToken() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resetreuseuser", "resetreuseuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));
        outboxEventRepository.deleteAllInBatch();
        requestPasswordReset("resetreuseuser@example.com");
        PasswordResetOutboxPayload payload = passwordResetPayload();
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                payload.recipientEmail(),
                payload.resetToken(),
                "newPassword123",
                "newPassword123"
        );

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    /**
     * 다른 이메일과 토큰을 조합해도 비밀번호 재설정을 허용하지 않는지 확인한다.
     */
    @Test
    void otherUserResetToken() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest("resetowner", "resetowner@example.com", "password123", 1998, null, "ko", "KR"))));
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SignupRequest("resetother", "resetother@example.com", "password123", 1998, null, "ko", "KR"))));
        outboxEventRepository.deleteAllInBatch();
        requestPasswordReset("resetowner@example.com");
        PasswordResetOutboxPayload payload = passwordResetPayload();
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(
                "resetother@example.com",
                payload.resetToken(),
                "newPassword123",
                "newPassword123"
        );

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    /**
     * 갱신 요청이 접근 토큰과 갱신 쿠키를 반환하는지 확인한다. 마지막 토큰 비교는 helper의 추가 로그인으로 발급된 값과 비교한다.
     */
    @Test
    void refreshTokenContract() throws Exception {
        SignupRequest signupRequest = new SignupRequest("refreshuser", "refreshuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("refreshuser", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
        String refreshToken = refreshTokenOf("refreshuser");

        MvcResult refreshResult = mockMvc.perform(post("/auth/token/refresh")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        org.junit.jupiter.api.Assertions.assertTrue(
                refreshResult.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("PINGDOM_REFRESH_TOKEN=")
        );
        org.junit.jupiter.api.Assertions.assertNotEquals(refreshToken, refreshTokenOf("refreshuser"));
    }

    /**
     * 갱신 쿠키 이름에 접근 토큰을 넣어도 토큰 유형 검증에서 거절되는지 확인한다.
     */
    @Test
    void accessTokenAsRefreshCookie() throws Exception {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "tester", "USER");

        mockMvc.perform(post("/auth/token/refresh")
                        .cookie(refreshTokenCookie(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 반복 로그아웃은 204를 유지하고 쿠키·저장된 갱신 토큰을 제거해 이전 토큰의 갱신을 차단하는지 확인한다.
     */
    @Test
    void idempotentLogout() throws Exception {
        SignupRequest signupRequest = new SignupRequest("logoutuser", "logoutuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("logoutuser", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
        String refreshToken = refreshTokenOf("logoutuser");

        MvcResult logoutResult = mockMvc.perform(post("/auth/logout")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(
                logoutResult.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Max-Age=0")
        );

        mockMvc.perform(post("/auth/logout")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isNoContent());

        User user = userRepository.findByUsername("logoutuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(user.getRefreshToken());

        mockMvc.perform(post("/auth/token/refresh")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 활성 임시 제재 뒤 기존 접근은 INVALID_TOKEN, 갱신은 USER_BANNED로 각각 차단되는지 확인한다.
     */
    @Test
    void activeBanTokens() throws Exception {
        SignupRequest signupRequest = new SignupRequest("activebanuser", "activebanuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("activebanuser", "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
        String refreshToken = refreshTokenOf("activebanuser");

        User user = userRepository.findByUsername("activebanuser").orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        user.ban("기간 밴 테스트", now, now.plusDays(1));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(post("/auth/token/refresh")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));
    }

    /**
     * 임시 제재가 만료된 사용자는 기존 접근 토큰 조회와 새 로그인 모두 성공하는지 확인한다.
     */
    @Test
    void expiredBanAccess() throws Exception {
        SignupRequest signupRequest = new SignupRequest("expiredbanuser", "expiredbanuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        String accessToken = loginAndExtractAccessToken("expiredbanuser");
        User user = userRepository.findByUsername("expiredbanuser").orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        user.ban("만료된 기간 밴", now.minusDays(2), now.minusDays(1));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        LoginRequest loginRequest = new LoginRequest("expiredbanuser", "password123");
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    /**
     * 탈퇴가 이름·이메일 익명화와 프로필·갱신 토큰·기기 토큰 정리로 이어지고 기존 접근·갱신 요청을 차단하는지 확인한다.
     */
    @Test
    void withdrawIdentityAndTokens() throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                "withdrawuser",
                "withdrawuser@example.com",
                "password123",
                1998,
                "https://example.com/profile.jpg",
                "ko",
                "KR"
        );
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        User signedUpUser = userRepository.findByUsername("withdrawuser").orElseThrow();
        fcmDeviceTokenRepository.saveAndFlush(
                FcmDeviceToken.create(signedUpUser.getId(), "fcm-token", LocalDateTime.now())
        );

        LoginRequest loginRequest = new LoginRequest("withdrawuser", "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
        String refreshToken = refreshTokenOf("withdrawuser");

        mockMvc.perform(delete("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        User withdrawnUser = userRepository.findById(signedUpUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(withdrawnUser.isWithdrawn());
        org.junit.jupiter.api.Assertions.assertNotNull(withdrawnUser.getWithdrawnAt());
        org.junit.jupiter.api.Assertions.assertEquals("withdrawn_user_" + signedUpUser.getId(), withdrawnUser.getUsername());
        org.junit.jupiter.api.Assertions.assertEquals("withdrawn_user_%d@withdrawn.local".formatted(signedUpUser.getId()), withdrawnUser.getEmail());
        org.junit.jupiter.api.Assertions.assertNull(withdrawnUser.getProfileImageUrl());
        org.junit.jupiter.api.Assertions.assertNull(withdrawnUser.getRefreshToken());
        org.junit.jupiter.api.Assertions.assertTrue(
                fcmDeviceTokenRepository.findAllByUserIdOrderByUpdatedAtDesc(signedUpUser.getId()).isEmpty()
        );
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByUsername("withdrawuser").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByEmail("withdrawuser@example.com").isEmpty());

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(post("/auth/token/refresh")
                        .cookie(refreshTokenCookie(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
    }

    /**
     * 탈퇴 전 사용자명과 이메일을 새 가입에서 다시 사용할 수 있는지 확인한다.
     */
    @Test
    void reuseWithdrawnIdentity() throws Exception {
        SignupRequest signupRequest = new SignupRequest("reuseuser", "reuseuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        String accessToken = loginAndExtractAccessToken("reuseuser");

        mockMvc.perform(delete("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("reuseuser"))
                .andExpect(jsonPath("$.email").value("reuseuser@example.com"));
    }

    /**
     * 탈퇴 후 게시물·장소 작성자 표시는 익명화하고 좋아요·북마크·알림을 삭제하는지 확인한다.
     */
    @Test
    void withdrawContentAndActivity() throws Exception {
        SignupRequest signupRequest = new SignupRequest("contentuser", "contentuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        User user = userRepository.findByUsername("contentuser").orElseThrow();
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("장소")
                .address("주소")
                .latitude(35.1)
                .longitude(128.1)
                .userId(user.getId())
                .registrant(user.getUsername())
                .build());
        MapImage mapImage = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/image.jpg")
                .s3Key("map/image.jpg")
                .title("제목")
                .description("설명")
                .userId(user.getId())
                .username(user.getUsername())
                .likeCount(0)
                .mapPlace(mapPlace)
                .build());
        mapImageLikeRepository.save(MapImageLike.builder()
                .userId(user.getId())
                .mapImageId(mapImage.getId())
                .build());
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(user.getId())
                .placeId(mapPlace.getId())
                .build());
        notificationsRepository.save(Notifications.builder()
                .token("fcm-token")
                .type(NotificationType.NEW_LIKE)
                .userId(user.getId())
                .title("알림")
                .body("본문")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build());

        String accessToken = loginAndExtractAccessToken("contentuser");

        mockMvc.perform(delete("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertEquals(
                User.WITHDRAWN_DISPLAY_NAME,
                mapImageRepository.findById(mapImage.getId()).orElseThrow().getUsername()
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                User.WITHDRAWN_DISPLAY_NAME,
                mapPlaceRepository.findById(mapPlace.getId()).orElseThrow().getRegistrant()
        );
        org.junit.jupiter.api.Assertions.assertTrue(mapImageLikeRepository.findAll().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(mapBookmarkRepository.findAll().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(notificationsRepository.findAll().isEmpty());
    }

    /**
     * 탈퇴 31일 뒤 사용자 행을 제거해도 장소·게시물은 남기고 작성자 ID만 null로 분리하는지 확인한다.
     */
    @Test
    void purgeWithdrawnUser() {
        User user = userRepository.saveAndFlush(User.builder()
                .username("purgeuser")
                .email("purgeuser@example.com")
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .build());
        MapPlace mapPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("삭제 유예 장소")
                .address("주소")
                .latitude(35.1)
                .longitude(128.1)
                .userId(user.getId())
                .registrant(User.WITHDRAWN_DISPLAY_NAME)
                .build());
        MapImage mapImage = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/purge.jpg")
                .s3Key("map/purge.jpg")
                .title("삭제 유예 게시글")
                .description("설명")
                .userId(user.getId())
                .username(User.WITHDRAWN_DISPLAY_NAME)
                .likeCount(0)
                .mapPlace(mapPlace)
                .build());
        LocalDateTime now = LocalDateTime.now();
        user.withdraw(
                "withdrawn_user_" + user.getId(),
                "withdrawn_user_%d@withdrawn.local".formatted(user.getId()),
                "encoded-random-password",
                now.minusDays(31)
        );
        userRepository.saveAndFlush(user);

        int purgedCount = withdrawnUserPurgeService.purgeExpiredUsers(now);

        org.junit.jupiter.api.Assertions.assertEquals(1, purgedCount);
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findById(user.getId()).isEmpty());
        org.junit.jupiter.api.Assertions.assertNull(mapPlaceRepository.findById(mapPlace.getId()).orElseThrow().getUserId());
        org.junit.jupiter.api.Assertions.assertNull(mapImageRepository.findById(mapImage.getId()).orElseThrow().getUserId());
    }

    /**
     * 공통 비밀번호로 실제 로그인한 응답에서 접근 토큰을 추출한다.
     */
    private String loginAndExtractAccessToken(String username) throws Exception {
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
     * 저장된 값을 읽는 대신 추가 로그인을 수행해 새로 발급된 갱신 쿠키 값을 반환한다.
     */
    private String refreshTokenOf(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return loginResult.getResponse().getCookie(REFRESH_TOKEN_COOKIE_NAME).getValue();
    }

    /**
     * 갱신 토큰을 인증 API에서 읽는 공통 쿠키 이름으로 감싼다.
     */
    private Cookie refreshTokenCookie(String refreshToken) {
        return new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    }

    /**
     * 지정 이메일의 재설정 요청을 보내고 200 응답을 확인한다.
     */
    private void requestPasswordReset(String email) throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());
    }

    /**
     * 첫 PASSWORD_RESET_REQUESTED outbox를 찾아 payload를 역직렬화하며 누락이나 잘못된 JSON은 실패시킨다.
     */
    private PasswordResetOutboxPayload passwordResetPayload() throws Exception {
        return outboxEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == OutboxEventType.PASSWORD_RESET_REQUESTED)
                .findFirst()
                .map(event -> {
                    try {
                        return objectMapper.readValue(event.getPayload(), PasswordResetOutboxPayload.class);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .orElseThrow();
    }

    /**
     * 원문 토큰의 UTF-8 SHA-256을 16진 문자열로 만들어 만료 토큰 fixture에 사용한다.
     */
    private String passwordResetTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
