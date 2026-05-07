package com.typenull.pingdom.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.dto.email.EmailVerifyRequest;
import com.typenull.pingdom.domain.auth.email.EmailSender;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.auth.dto.token.RefreshTokenRequest;
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
import com.fasterxml.jackson.databind.ObjectMapper;
@SpringBootTest
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
        userRepository.deleteAll();
    }

    @Test
    void signupCreatesUser() throws Exception {
        SignupRequest request = new SignupRequest("tester01", "tester", "tester01@example.com", "password123");

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("tester01"))
                .andExpect(jsonPath("$.name").value("tester"));

        User user = userRepository.findByUsername("tester01").orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(user.getEmailVerificationCode());
        org.junit.jupiter.api.Assertions.assertFalse(user.isEmailVerified());
    }

    @Test
    void loginReturnsTokensWhenCredentialsAreValid() throws Exception {
        SignupRequest signupRequest = new SignupRequest("loginuser", "tester", "loginuser@example.com", "password123");
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
        SignupRequest signupRequest = new SignupRequest("failuser", "tester", "failuser@example.com", "password123");
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
        SignupRequest signupRequest = new SignupRequest("emailuser", "tester", "emailuser@example.com", "password123");
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
    void refreshTokenReissuesTokensWhenRefreshTokenIsValid() throws Exception {
        SignupRequest signupRequest = new SignupRequest("refreshuser", "tester", "refreshuser@example.com", "password123");
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
    void withdrawDeletesUserWhenAccessTokenIsValid() throws Exception {
        SignupRequest signupRequest = new SignupRequest("withdrawuser", "tester", "withdrawuser@example.com", "password123");
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
