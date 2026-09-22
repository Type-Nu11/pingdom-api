package com.typenull.pingdom.integration.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 세부 역할과 감사 로그 필터·페이지 계약을 실제 로그인 및 저장 데이터로 검증한다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAuditLogControllerTest {

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

    /**
     * 감사 로그와 관리자 역할을 사용자보다 먼저 비워 조회 결과를 격리한다.
     */
    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        adminRoleAssignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * ANALYST는 감사 로그를 조회하지만 SUPPORT_OPERATOR는 ADMIN_PERMISSION_REQUIRED로 거절되는지 확인한다.
     */
    @Test
    void auditReadPermissions() throws Exception {
        String analystAccessToken = createAdminAndLogin("auditAnalyst", AdminRole.ANALYST);
        String supportAccessToken = createAdminAndLogin("auditSupport", AdminRole.SUPPORT_OPERATOR);

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + analystAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supportAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    /**
     * 행위·대상 종류·대상 ID·기간을 함께 적용한 결과가 해당 감사 로그 한 건인지 확인한다.
     */
    @Test
    void combinedAuditFilters() throws Exception {
        String adminAccessToken = createUserAndLogin("auditAdmin", UserRole.ADMIN);

        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(1L)
                .actorUsername("auditAdmin")
                .action(AdminAuditAction.USER_BAN_APPLIED)
                .targetType(AdminAuditTargetType.USER)
                .targetId("7")
                .reason("반복 신고")
                .beforeState("{\"banned\":false}")
                .afterState("{\"banned\":true}")
                .requestId("audit-request-1")
                .createdAt(LocalDateTime.of(2026, 6, 24, 10, 0))
                .build());
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(1L)
                .actorUsername("auditAdmin")
                .action(AdminAuditAction.AD_CREATED)
                .targetType(AdminAuditTargetType.AD)
                .targetId("3")
                .reason("AD_CREATED")
                .afterState("{\"deleted\":false}")
                .requestId("audit-request-2")
                .createdAt(LocalDateTime.of(2026, 6, 24, 11, 0))
                .build());

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("action", AdminAuditAction.USER_BAN_APPLIED.name())
                        .param("targetType", AdminAuditTargetType.USER.name())
                        .param("targetId", "7")
                        .param("from", "2026-06-24T00:00:00")
                        .param("to", "2026-06-24T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs.length()").value(1))
                .andExpect(jsonPath("$.auditLogs[0].action").value(AdminAuditAction.USER_BAN_APPLIED.name()))
                .andExpect(jsonPath("$.auditLogs[0].targetType").value(AdminAuditTargetType.USER.name()))
                .andExpect(jsonPath("$.auditLogs[0].targetId").value("7"))
                .andExpect(jsonPath("$.auditLogs[0].requestId").value("audit-request-1"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    /**
     * 선택 필터를 생략한 조회와 전체 삭제 후 빈 페이지의 건수·페이지 수·다음 페이지 여부를 확인한다.
     */
    @Test
    void defaultAndEmptyAuditPage() throws Exception {
        String adminAccessToken = createUserAndLogin("auditDefaultAdmin", UserRole.ADMIN);

        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(1L)
                .actorUsername("auditDefaultAdmin")
                .action(AdminAuditAction.USER_BAN_APPLIED)
                .targetType(AdminAuditTargetType.USER)
                .targetId("7")
                .reason("반복 신고")
                .requestId("audit-default-request")
                .createdAt(LocalDateTime.of(2026, 8, 16, 12, 0))
                .build());

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs.length()").value(1))
                .andExpect(jsonPath("$.auditLogs[0].requestId").value("audit-default-request"))
                .andExpect(jsonPath("$.totalCount").value(1));

        adminAuditLogRepository.deleteAllInBatch();

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 시작이 종료보다 늦은 기간이 INVALID_AUDIT_LOG_FILTER_PERIOD로 거절되는지 확인한다.
     */
    @Test
    void reversedAuditPeriod() throws Exception {
        String adminAccessToken = createUserAndLogin("auditPeriodAdmin", UserRole.ADMIN);

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("from", "2026-06-25T00:00:00")
                        .param("to", "2026-06-24T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUDIT_LOG_FILTER_PERIOD"));
    }

    /**
     * 일반 사용자 토큰으로 감사 로그 조회 시 403과 ACCESS_DENIED를 확인한다.
     */
    @Test
    void auditRejectsUser() throws Exception {
        String userAccessToken = createUserAndLogin("auditNormalUser", UserRole.USER);

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    /**
     * 지정 역할의 사용자를 저장하며 ADMIN이면 SUPER_ADMIN을 배정한 뒤 실제 로그인 토큰을 반환한다.
     */
    private String createUserAndLogin(String username, UserRole role) throws Exception {
        User user = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());
        if (role == UserRole.ADMIN) {
            adminRoleAssignmentRepository.save(AdminRoleAssignment.assign(
                    user.getId(), AdminRole.SUPER_ADMIN, user.getId(), LocalDateTime.now()
            ));
        }

        return login(username);
    }

    /**
     * 지정한 세부 관리자 역할을 배정해 권한 경계를 검증할 로그인 토큰을 만든다.
     */
    private String createAdminAndLogin(String username, AdminRole adminRole) throws Exception {
        User admin = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());
        adminRoleAssignmentRepository.save(AdminRoleAssignment.assign(
                admin.getId(), adminRole, admin.getId(), LocalDateTime.now()
        ));

        return login(username);
    }

    /**
     * 공통 비밀번호로 로그인 성공을 확인하고 JSON 접근 토큰을 추출한다.
     */
    private String login(String username) throws Exception {
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
