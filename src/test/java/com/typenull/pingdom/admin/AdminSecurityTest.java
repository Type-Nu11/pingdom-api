package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
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
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void adminEndpointRejectsUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void adminEndpointRejectsNonAdminUser() throws Exception {
        createUser("normalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("normalUser");

        mockMvc.perform(get("/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
    }

    @Test
    void adminEndpointAllowsAdminUser() throws Exception {
        createUser("adminUser", UserRole.ADMIN);
        String accessToken = loginAndGetAccessToken("adminUser");

        mockMvc.perform(get("/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private void createUser(String username, UserRole role) {
        userRepository.save(User.builder()
                .username(username)
                .name("tester")
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .build());
    }

    private String loginAndGetAccessToken(String username) throws Exception {
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
}
