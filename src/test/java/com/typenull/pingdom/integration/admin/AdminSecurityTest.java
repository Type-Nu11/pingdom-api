package com.typenull.pingdom.integration.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
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
import org.junit.jupiter.api.Tag;
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

/**
 * 관리자 경로와 로그인·감사 조회에서 미인증, 사용자 역할, 탈퇴 상태에 따른 접근 경계를 검증.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret"
})
@AutoConfigureMockMvc
@Transactional
class AdminSecurityTest {

    @MockBean
    private RateLimitStore rateLimitStore;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRoleAssignmentRepository adminRoleAssignmentRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private Clock clock;

    /**
     * 감사 로그·관리자 역할·사용자를 삭제해 인증 및 역할 경계 검증을 격리.
     */
    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        adminRoleAssignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 게시물 관리자 경로가 토큰 없는 요청에 401과 INVALID_TOKEN을 반환하는지 확인.
     */
    @Test
    void postsRequireToken() throws Exception {
        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 일반 사용자의 관리자 게시물 접근에서 JSON 403 응답과 관리자 권한 안내를 확인.
     */
    @Test
    void postsRejectUser() throws Exception {
        createUser("normalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("normalUser");

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
    }

    /**
     * 요약 대시보드가 토큰 없는 요청을 INVALID_TOKEN으로 거절하는지 확인.
     */
    @Test
    void summaryRequiresToken() throws Exception {
        mockMvc.perform(get("/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 일반 사용자 토큰으로 요약 대시보드 조회 시 ACCESS_DENIED를 확인.
     */
    @Test
    void summaryRejectsUser() throws Exception {
        createUser("dashboardNormalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("dashboardNormalUser");

        mockMvc.perform(get("/admin/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 최근 활동 조회의 미인증 요청이 401인지 확인.
     */
    @Test
    void activitiesRequireToken() throws Exception {
        mockMvc.perform(get("/admin/dashboard/recent-activities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 최근 활동 조회의 일반 사용자 요청이 403인지 확인.
     */
    @Test
    void activitiesRejectUser() throws Exception {
        createUser("recentActivitiesNormalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("recentActivitiesNormalUser");

        mockMvc.perform(get("/admin/dashboard/recent-activities")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 대기 항목 조회가 일반 사용자 역할을 ACCESS_DENIED로 거절하는지 확인.
     */
    @Test
    void pendingItemsRejectUser() throws Exception {
        createUser("dashboardPendingNormalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("dashboardPendingNormalUser");

        mockMvc.perform(get("/admin/dashboard/pending-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 대기 항목 조회가 미인증 요청을 INVALID_TOKEN으로 거절하는지 확인.
     */
    @Test
    void pendingItemsRequireToken() throws Exception {
        mockMvc.perform(get("/admin/dashboard/pending-items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 신고자 신뢰도 조회가 토큰 없는 요청에 401을 반환하는지 확인.
     */
    @Test
    void trustScoreRequiresToken() throws Exception {
        mockMvc.perform(get("/admin/trust-score/reporters/{reporterUserId}", 7L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 신고자 신뢰도 조회가 일반 사용자에게 403을 반환하는지 확인.
     */
    @Test
    void trustScoreRejectsUser() throws Exception {
        createUser("trustScoreNormalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("trustScoreNormalUser");

        mockMvc.perform(get("/admin/trust-score/reporters/{reporterUserId}", 7L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 신뢰도 개입 평가 요청이 미인증 상태에서는 401인지 확인.
     */
    @Test
    void interventionRequiresToken() throws Exception {
        mockMvc.perform(post("/admin/trust-score/reporters/{reporterUserId}/interventions/evaluate", 7L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 일반 사용자의 신뢰도 개입 평가 요청이 ACCESS_DENIED로 거절되는지 확인.
     */
    @Test
    void interventionRejectsUser() throws Exception {
        createUser("trustScoreInterventionNormalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("trustScoreInterventionNormalUser");

        mockMvc.perform(post("/admin/trust-score/reporters/{reporterUserId}/interventions/evaluate", 7L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 형식상 유효한 온보딩 변경 본문이어도 미인증 요청은 401인지 확인.
     */
    @Test
    void onboardingRequiresToken() throws Exception {
        mockMvc.perform(put("/admin/merchant-owners/{userId}/onboarding", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "COMPLETED",
                                  "completionRate": 100,
                                  "reason": "온보딩 완료"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    /**
     * 운영 품질 변경 본문을 보낸 일반 사용자 요청이 403인지 확인.
     */
    @Test
    void merchantQualityRejectsUser() throws Exception {
        createUser("merchantMetricNormalUser", UserRole.USER);
        String accessToken = loginAndGetAccessToken("merchantMetricNormalUser");

        mockMvc.perform(put("/admin/merchant-owners/{userId}/places/{placeId}/quality", 7L, 10L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "HEALTHY",
                                  "reservationResponseRate": 95,
                                  "reservationCancellationRate": 2,
                                  "noShowRate": 1,
                                  "reason": "운영 품질 확인"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 관리자 로그인 후 빈 데이터의 대시보드 집계 네 항목이 모두 0인지 확인.
     */
    @Test
    void adminDashboardSummary() throws Exception {
        createUser("dashboardAdmin", UserRole.ADMIN);
        String accessToken = loginAndGetAccessToken("dashboardAdmin");

        mockMvc.perform(get("/admin/dashboard/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeCount").value(0))
                .andExpect(jsonPath("$.postCount").value(0))
                .andExpect(jsonPath("$.pendingReportCount").value(0))
                .andExpect(jsonPath("$.bannedUserCount").value(0));
    }

    /**
     * 관리자 로그인 경로에서는 일반 사용자의 올바른 자격 증명도 INVALID_CREDENTIALS로 거절하는지 확인.
     */
    @Test
    void adminLoginRejectsUser() throws Exception {
        createUser("normalAdminPageUser", UserRole.USER);
        LoginRequest loginRequest = new LoginRequest("normalAdminPageUser", "password123");

        mockMvc.perform(post("/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    /**
     * 관리자 로그인은 접근 토큰을 본문에, 갱신 토큰을 쿠키에 반환하고 본문에는 갱신 토큰을 노출하지 않는지 확인.
     */
    @Test
    void adminLoginCookie() throws Exception {
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

    /**
     * 관리자 로그인에서 발급한 접근 토큰의 역할 claim이 ADMIN인지 확인.
     */
    @Test
    void adminTokenRole() throws Exception {
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

        assertThat(jwtTokenProvider.getRoleFromAccessToken(accessToken)).isEqualTo("ADMIN");
    }

    /**
     * 관리자 토큰으로 게시물 목록을 조회할 때 이 시나리오가 기대하는 200 응답을 확인.
     */
    @Test
    void adminPostsAccess() throws Exception {
        createUser("adminUser", UserRole.ADMIN);
        String accessToken = loginAndGetAccessToken("adminUser");

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    /**
     * 관리자가 기간 내 감사 두 건을 최신순으로 조회하고 대상·행위·요청 ID를 확인할 수 있는지 검증.
     */
    @Test
    void adminAuditFixture() throws Exception {
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

    /**
     * 상점 소유자 역할은 감사 로그에 접근할 수 없고 관리자 권한 안내를 받는지 확인.
     */
    @Test
    void auditRejectsMerchantOwner() throws Exception {
        SecurityRegressionFixture fixture = securityRegressionFixture();

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.merchantOwnerAccessToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
    }

    /**
     * 토큰 발급 뒤 탈퇴한 관리자의 기존 토큰도 감사 로그 접근 시 무효 처리되는지 확인.
     */
    @Test
    void auditRejectsWithdrawnAdmin() throws Exception {
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

    /**
     * 관리자·상점 소유자·탈퇴 예정 관리자와 시간순 감사 두 건을 저장하고 각 접근 토큰을 묶음.
     */
    private SecurityRegressionFixture securityRegressionFixture() throws Exception {
        User admin = createUser("securityAuditAdmin", UserRole.ADMIN);
        adminRoleAssignmentRepository.save(AdminRoleAssignment.assign(
                admin.getId(), AdminRole.SUPER_ADMIN, admin.getId(), LocalDateTime.now()
        ));
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

    /**
     * 지정 역할의 사용자를 저장하고 flush하여 이후 인증 필터가 읽을 수 있게 함.
     */
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

    /**
     * 일반 로그인 경로의 성공 응답에서 접근 토큰을 추출.
     */
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
