package com.typenull.pingdom.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.email.EmailResendRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.api.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
import java.time.LocalDateTime;
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
            return (recipientEmail, verificationCode) -> {
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
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
        org.junit.jupiter.api.Assertions.assertTrue(updatedUser.getEmailVerificationExpiresAt().isAfter(LocalDateTime.now()));

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
    void resendEmailFailsWhenUserDoesNotExist() throws Exception {
        EmailResendRequest resendRequest = new EmailResendRequest("missing@example.com");

        mockMvc.perform(post("/auth/email/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
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
    void withdrawDeletesUserWhenAccessTokenIsValid() throws Exception {
        SignupRequest signupRequest = new SignupRequest("withdrawuser", "withdrawuser@example.com", "password123", 1998, null, "ko", "KR");
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)));

        LoginRequest loginRequest = new LoginRequest("withdrawuser", "password123");
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();

        mockMvc.perform(delete("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findByUsername("withdrawuser").isEmpty());
    }
}
