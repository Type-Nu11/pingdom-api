package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "fcm.enabled=false",
        "fcm.key-path=dummy"
})
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest {

    @MockBean
    private S3Client s3Client;

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
        userRepository.deleteAllInBatch();
    }

    @Test
    void listBannedUsersReturnsOnlyBannedUsersOrderedByBannedAtDesc() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        User olderBannedUser = createUser("bannedUser01");
        olderBannedUser.ban("첫 번째 밴", LocalDateTime.of(2026, 6, 5, 10, 0));
        userRepository.save(olderBannedUser);

        User activeUser = createUser("activeUser01");

        User newerBannedUser = createUser("bannedUser02");
        newerBannedUser.ban("두 번째 밴", LocalDateTime.of(2026, 6, 6, 11, 0));
        userRepository.save(newerBannedUser);

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].userId").value(newerBannedUser.getId()))
                .andExpect(jsonPath("$.users[0].username").value("bannedUser02"))
                .andExpect(jsonPath("$.users[0].banned").value(true))
                .andExpect(jsonPath("$.users[1].userId").value(olderBannedUser.getId()))
                .andExpect(jsonPath("$.users[1].username").value("bannedUser01"));
    }

    @Test
    void listBannedUsersReturnsEmptyListWhenNoBannedUserExists() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        createUser("activeUser02");

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(0));
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(User.builder()
                .username("adminTester")
                .email("admin@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());

        LoginRequest loginRequest = new LoginRequest("adminTester", "password123");
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
