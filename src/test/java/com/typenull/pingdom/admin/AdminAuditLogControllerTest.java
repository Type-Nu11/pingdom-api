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
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

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
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listAuditLogsFiltersByActionTargetAndPeriod() throws Exception {
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

    @Test
    void listAuditLogsSupportsDefaultFiltersAndEmptyResult() throws Exception {
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

    @Test
    void listAuditLogsRejectsInvalidPeriod() throws Exception {
        String adminAccessToken = createUserAndLogin("auditPeriodAdmin", UserRole.ADMIN);

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("from", "2026-06-25T00:00:00")
                        .param("to", "2026-06-24T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUDIT_LOG_FILTER_PERIOD"));
    }

    @Test
    void listAuditLogsRejectsNonAdminUser() throws Exception {
        String userAccessToken = createUserAndLogin("auditNormalUser", UserRole.USER);

        mockMvc.perform(get("/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private String createUserAndLogin(String username, UserRole role) throws Exception {
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());

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
