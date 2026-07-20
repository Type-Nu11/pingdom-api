package com.typenull.pingdom.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.Clock;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret"
})
@AutoConfigureMockMvc
@Transactional
class AdminSecurityTest {

    @TestConfiguration
    static class TestRateLimitConfig {

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
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void adminEndpointRejectsUnauthenticatedUser() throws Exception {
        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void adminEndpointRejectsNonAdminUser() throws Exception {
        createUser("normalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("normalUser");

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
    }

    @Test
    void adminLoginRejectsNonAdminUser() throws Exception {
        createUser("normalAdminPageUser", UserRole.USER);
        LoginRequest loginRequest = new LoginRequest("normalAdminPageUser", "password123");

        mockMvc.perform(post("/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void adminLoginAllowsAdminUser() throws Exception {
        createUser("adminLoginUser", UserRole.ADMIN);
        LoginRequest loginRequest = new LoginRequest("adminLoginUser", "password123");

        mockMvc.perform(post("/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("adminLoginUser"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("PINGDOM_REFRESH_TOKEN=")));
    }

    @Test
    void adminLoginAccessTokenContainsAdminRoleClaim() throws Exception {
        createUser("adminClaimUser", UserRole.ADMIN);
        LoginRequest loginRequest = new LoginRequest("adminClaimUser", "password123");

        MvcResult loginResult = mockMvc.perform(post("/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();

        org.junit.jupiter.api.Assertions.assertEquals("ADMIN", jwtTokenProvider.getRoleFromAccessToken(accessToken));
    }

    @Test
    void adminEndpointAllowsAdminUser() throws Exception {
        createUser("adminUser", UserRole.ADMIN);
        String accessToken = loginAndGetAccessToken("adminUser");

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminAuditScenarioAllowsAdminToReviewSecurityFixture() throws Exception {
        SecurityRegressionFixture fixture = securityRegressionFixture();

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.adminAccessToken())
                        .param("from", "2026-07-01T00:00:00")
                        .param("to", "2026-07-01T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs.length()").value(2))
                .andExpect(jsonPath("$.auditLogs[0].action").value(AdminAuditAction.REPORT_ACCEPTED.name()))
                .andExpect(jsonPath("$.auditLogs[0].targetType").value(AdminAuditTargetType.REPORT.name()))
                .andExpect(jsonPath("$.auditLogs[0].targetId").value("501"))
                .andExpect(jsonPath("$.auditLogs[0].requestId").value("security-regression-success"))
                .andExpect(jsonPath("$.auditLogs[1].action").value(AdminAuditAction.USER_BAN_APPLIED.name()))
                .andExpect(jsonPath("$.auditLogs[1].targetType").value(AdminAuditTargetType.USER.name()))
                .andExpect(jsonPath("$.auditLogs[1].targetId").value(String.valueOf(fixture.targetUserId())))
                .andExpect(jsonPath("$.auditLogs[1].requestId").value("security-regression-boundary"))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    void adminAuditScenarioRejectsMerchantOwnerBoundaryRole() throws Exception {
        SecurityRegressionFixture fixture = securityRegressionFixture();

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.merchantOwnerAccessToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
    }

    @Test
    void adminAuditScenarioRejectsWithdrawnAdminExistingToken() throws Exception {
        SecurityRegressionFixture fixture = securityRegressionFixture();

        User withdrawnAdmin = userRepository.findById(fixture.withdrawnAdminId()).orElseThrow();
        withdrawnAdmin.withdraw(
                "withdrawn_admin_" + withdrawnAdmin.getId(),
                "withdrawn_admin_%d@withdrawn.local".formatted(withdrawnAdmin.getId()),
                "encoded-withdrawn-password",
                LocalDateTime.now(clock)
        );
        userRepository.saveAndFlush(withdrawnAdmin);

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.withdrawnAdminAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    private SecurityRegressionFixture securityRegressionFixture() throws Exception {
        User admin = createUser("securityAuditAdmin", UserRole.ADMIN);
        User merchantOwner = createUser("securityAuditMerchant", UserRole.MERCHANT_OWNER);
        User withdrawnAdmin = createUser("securityAuditWithdrawnAdmin", UserRole.ADMIN);
        User targetUser = createUser("securityAuditTarget", UserRole.USER);

        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(admin.getId())
                .actorUsername(admin.getUsername())
                .action(AdminAuditAction.USER_BAN_APPLIED)
                .targetType(AdminAuditTargetType.USER)
                .targetId(String.valueOf(targetUser.getId()))
                .reason("반복 허위 신고 경계 케이스")
                .beforeState("{\"banned\":false,\"role\":\"USER\"}")
                .afterState("{\"banned\":true,\"banType\":\"TEMPORARY\"}")
                .requestId("security-regression-boundary")
                .createdAt(LocalDateTime.of(2026, 7, 1, 10, 0))
                .build());
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(admin.getId())
                .actorUsername(admin.getUsername())
                .action(AdminAuditAction.REPORT_ACCEPTED)
                .targetType(AdminAuditTargetType.REPORT)
                .targetId("501")
                .reason("명확한 위반 신고 정상 처리")
                .beforeState("{\"status\":\"PENDING\"}")
                .afterState("{\"status\":\"ACCEPTED\"}")
                .requestId("security-regression-success")
                .createdAt(LocalDateTime.of(2026, 7, 1, 11, 0))
                .build());

        return new SecurityRegressionFixture(
                loginAndGetAccessToken(admin.getUsername()),
                loginAndGetAccessToken(merchantOwner.getUsername()),
                loginAndGetAccessToken(withdrawnAdmin.getUsername()),
                withdrawnAdmin.getId(),
                targetUser.getId()
        );
    }

    private User createUser(String username, UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
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

    private record SecurityRegressionFixture(
            String adminAccessToken,
            String merchantOwnerAccessToken,
            String withdrawnAdminAccessToken,
            Long withdrawnAdminId,
            Long targetUserId
    ) {
    }
}
