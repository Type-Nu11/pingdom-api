package com.typenull.pingdom.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.application.service.WithdrawnUserPurgeService;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetConfirmRequest;
import com.typenull.pingdom.identity.api.dto.passwordreset.PasswordResetRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.PasswordResetToken;
import com.typenull.pingdom.identity.domain.repository.OAuthAccountRepository;
import com.typenull.pingdom.identity.domain.repository.PasswordResetTokenRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import com.typenull.pingdom.notification.outbox.PasswordResetOutboxPayload;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import com.typenull.pingdom.shared.ratelimit.RateLimitStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret"
})
@AutoConfigureMockMvc
class AuthControllerTest {

    @TestConfiguration
    static class TestEmailSenderConfig {

        @Bean
        @Primary
        // 테스트용 메일 발송 대체 빈
        EmailSender emailSender() {
            return new EmailSender() {
                @Override
                public void sendVerificationEmail(String recipientEmail, String verificationCode) {
                }

                @Override
                public void sendPasswordResetEmail(String recipientEmail, String resetToken, LocalDateTime expiresAt) {
                }
            };
        }

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
    private WithdrawnUserPurgeService withdrawnUserPurgeService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        notificationsRepository.deleteAllInBatch();
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        oAuthAccountRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        passwordResetTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

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

    @Test
    void loginReturnsTokensWhenCredentialsAreValid() throws Exception {
        SignupRequest signupRequest = new SignupRequest("loginuser", "loginuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("loginuser", "password123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void loginFailsWhenPasswordIsInvalid() throws Exception {
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

    @Test
    void verifyEmailMarksUserAsVerified() throws Exception {
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

    @Test
    void resendEmailReissuesVerificationCodeAndOnlyNewCodeCanVerify() throws Exception {
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

    @Test
    void resendEmailFailsWhenUserAlreadyVerified() throws Exception {
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

    @Test
    void resendEmailFailsWhenUserIsBanned() throws Exception {
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

    @Test
    void resendEmailFailsWhenUserDoesNotExist() throws Exception {
        EmailResendRequest resendRequest = new EmailResendRequest("missing@example.com");

        mockMvc.perform(post("/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void passwordResetRequestCreatesTokenAndOutboxEvent() throws Exception {
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

    @Test
    void passwordResetRequestFindsEmailIgnoringCase() throws Exception {
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

    @Test
    void passwordResetRequestDoesNotRevealMissingEmail() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest("missing-reset@example.com");

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(passwordResetTokenRepository.findAll().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(outboxEventRepository.findAll().isEmpty());
    }

    @Test
    void passwordResetConfirmChangesPasswordAndInvalidatesRefreshToken() throws Exception {
        SignupRequest signupRequest = new SignupRequest("resetconfirmuser", "resetconfirmuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("resetconfirmuser", "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken")
                .textValue();

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("resetconfirmuser", "newPassword123"))))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetConfirmIgnoresEmailCase() throws Exception {
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

    @Test
    void passwordResetConfirmRejectsExpiredToken() throws Exception {
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

    @Test
    void passwordResetConfirmRejectsReusedToken() throws Exception {
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

    @Test
    void passwordResetConfirmRejectsOtherUserToken() throws Exception {
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

    @Test
    void refreshTokenReissuesTokensWhenRefreshTokenIsValid() throws Exception {
        SignupRequest signupRequest = new SignupRequest("refreshuser", "refreshuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("refreshuser", "password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken")
                .textValue();

        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void logoutIsIdempotentAndBlocksReissue() throws Exception {
        SignupRequest signupRequest = new SignupRequest("logoutuser", "logoutuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("logoutuser", "password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken")
                .textValue();

        RefreshTokenRequest logoutRequest = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        User user = userRepository.findByUsername("logoutuser").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNull(user.getRefreshToken());

        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void activeTemporaryBanBlocksExistingAccessAndRefreshTokens() throws Exception {
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
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken")
                .textValue();

        User user = userRepository.findByUsername("activebanuser").orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        user.ban("기간 밴 테스트", now, now.plusDays(1));
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BANNED"));
    }

    @Test
    void expiredTemporaryBanAllowsLoginAndExistingAccessToken() throws Exception {
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
                .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void withdrawAnonymizesUserAndBlocksExistingTokens() throws Exception {
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
        signedUpUser.updateFcmToken("fcm-token");
        userRepository.saveAndFlush(signedUpUser);

        LoginRequest loginRequest = new LoginRequest("withdrawuser", "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken")
                .textValue();

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
        org.junit.jupiter.api.Assertions.assertNull(withdrawnUser.getFcmToken());
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByUsername("withdrawuser").isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByEmail("withdrawuser@example.com").isEmpty());

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshTokenRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
    }

    @Test
    void withdrawnUsernameAndEmailCanBeReusedByNewSignup() throws Exception {
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

    @Test
    void withdrawAnonymizesContentDisplayAndDeletesUserOwnedData() throws Exception {
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

    @Test
    void purgeExpiredWithdrawnUsersDeletesAfterRetention() {
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

    private void requestPasswordReset(String email) throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordResetRequest(email))))
                .andExpect(status().isOk());
    }

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

    private String passwordResetTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
