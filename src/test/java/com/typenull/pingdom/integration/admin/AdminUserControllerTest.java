package com.typenull.pingdom.integration.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.user.sanction.UserSanctionCommandService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 관리자 역할에 따른 제재 조회·적용·해제와 이력·알림·감사의 연결을 검증한다.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key",
        "spring.security.oauth2.client.registration.google.client-id=test-google-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-client-secret",
        "fcm.enabled=false",
        "fcm.key-path=dummy",
        "abuse.rate-limit.login-username.limit=100"
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
    private AdminRoleAssignmentRepository adminRoleAssignmentRepository;

    @Autowired
    private UserSanctionHistoryRepository userSanctionHistoryRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private UserSanctionCommandService userSanctionCommandService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * outbox·감사·제재 이력·관리자 역할을 사용자보다 먼저 비워 제재 검증을 격리한다.
     */
    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        adminAuditLogRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
        adminRoleAssignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * SUPPORT_OPERATOR가 제재 사용자 목록을 조회하고 대상 사용자에게 제재를 적용할 수 있는지 확인한다.
     */
    @Test
    void supportSanctionPermissions() throws Exception {
        String adminAccessToken = createAdminAndLogin("supportOperator", AdminRole.SUPPORT_OPERATOR);
        User targetUser = createUser("supportOperatorTarget");

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/ban/{userId}", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"운영 제재\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banned").value(true));
    }

    /**
     * ANALYST의 제재 목록 조회와 제재 적용이 모두 ADMIN_PERMISSION_REQUIRED로 거절되는지 확인한다.
     */
    @Test
    void analystSanctionDenied() throws Exception {
        String adminAccessToken = createAdminAndLogin("analystOperator", AdminRole.ANALYST);
        User targetUser = createUser("analystOperatorTarget");

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));

        mockMvc.perform(post("/admin/ban/{userId}", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"권한 없음\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    /**
     * ADMIN 사용자라도 세부 역할이 없으면 제재 목록 조회를 거절하는지 확인한다.
     */
    @Test
    void unassignedAdminDenied() throws Exception {
        String adminAccessToken = createAdminAndLogin("unassignedAdmin", null);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));
    }

    /**
     * 비제재 사용자를 제외하고 최근 제재 순으로 두 영구 제재 사용자와 유형별 집계를 반환하는지 확인한다.
     */
    @Test
    void bannedUsersNewestFirst() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        User olderBannedUser = createUser("bannedUser01");
        olderBannedUser.ban("첫 번째 밴", LocalDateTime.of(2026, 6, 5, 10, 0));
        userRepository.save(olderBannedUser);

        User activeUser = createUser("activeUser01");

        User newerBannedUser = createUser("bannedUser02");
        newerBannedUser.ban("두 번째 밴", LocalDateTime.of(2026, 6, 6, 11, 0));
        userRepository.save(newerBannedUser);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].userId").value(newerBannedUser.getId()))
                .andExpect(jsonPath("$.users[0].username").value("bannedUser02"))
                .andExpect(jsonPath("$.users[0].banned").value(true))
                .andExpect(jsonPath("$.users[0].banType").value(UserBanType.PERMANENT.name()))
                .andExpect(jsonPath("$.users[0].bannedAt").value("2026-06-06T11:00:00"))
                .andExpect(jsonPath("$.users[1].userId").value(olderBannedUser.getId()))
                .andExpect(jsonPath("$.users[1].username").value("bannedUser01"))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.counts.total").value(2))
                .andExpect(jsonPath("$.counts.permanent").value(2))
                .andExpect(jsonPath("$.counts.temporary").value(0));
    }

    /**
     * 숫자 ID 검색과 사용자명 부분 검색으로 각각 해당 제재 사용자 한 명을 찾는지 확인한다.
     */
    @Test
    void bannedUserKeyword() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        User idMatchedUser = createUser("alphaBlocked");
        idMatchedUser.ban("아이디 검색", LocalDateTime.of(2026, 6, 5, 10, 0));
        userRepository.save(idMatchedUser);

        User usernameMatchedUser = createUser("keywordBlocked");
        usernameMatchedUser.ban("닉네임 검색", LocalDateTime.of(2026, 6, 6, 11, 0));
        userRepository.save(usernameMatchedUser);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", String.valueOf(idMatchedUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(idMatchedUser.getId()));

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", "keyword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.users[0].username").value("keywordBlocked"));
    }

    /**
     * 사용자명에 숫자가 포함돼도 숫자 검색어는 정확한 사용자 ID로만 해석하는지 확인한다.
     */
    @Test
    void numericKeywordIsUserId() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        User numericNameUser = createUser("user12345");
        numericNameUser.ban("닉네임 숫자 포함", LocalDateTime.of(2026, 6, 5, 10, 0));
        userRepository.save(numericNameUser);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", "12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(0));
    }

    /**
     * TEMPORARY 및 from/to 기간 필터와 만료일 오름차순 정렬을 함께 적용하는지 확인한다.
     */
    @Test
    void banPeriodAndExpirySort() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        LocalDateTime now = LocalDateTime.now().withNano(0);

        User permanentUser = createUser("permanentUser");
        permanentUser.ban("영구 밴", now.minusDays(5));
        userRepository.save(permanentUser);

        LocalDateTime temporaryUserEarlyBannedAt = now.minusDays(3);
        LocalDateTime temporaryUserEarlyExpiresAt = now.plusDays(10);
        User temporaryUserEarly = createUser("temporaryUserA");
        temporaryUserEarly.ban(
                "기간 밴 A",
                temporaryUserEarlyBannedAt,
                temporaryUserEarlyExpiresAt
        );
        userRepository.save(temporaryUserEarly);

        LocalDateTime temporaryUserLateBannedAt = now.minusDays(1);
        LocalDateTime temporaryUserLateExpiresAt = now.plusDays(12);
        User temporaryUserLate = createUser("temporaryUserB");
        temporaryUserLate.ban(
                "기간 밴 B",
                temporaryUserLateBannedAt,
                temporaryUserLateExpiresAt
        );
        userRepository.save(temporaryUserLate);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("banType", UserBanType.TEMPORARY.name())
                        .param("from", temporaryUserEarlyBannedAt.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("to", now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        .param("sortBy", "EXPIRES_AT")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].userId").value(temporaryUserEarly.getId()))
                .andExpect(jsonPath("$.users[0].banType").value(UserBanType.TEMPORARY.name()))
                .andExpect(jsonPath("$.users[0].banExpiresAt").value(temporaryUserEarlyExpiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.users[1].userId").value(temporaryUserLate.getId()))
                .andExpect(jsonPath("$.totalCount").value(2));

    }

    /**
     * 만료된 제재와 검색어 불일치 사용자를 제외한 집계가 페이지 크기 1과 무관하게 영구·임시 각각 한 건인지 확인한다.
     */
    @Test
    void filteredActiveBanCounts() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        LocalDateTime now = LocalDateTime.now();

        User permanentMatchedUser = createUser("keywordPermanent");
        permanentMatchedUser.ban("영구 밴", now.minusDays(3));
        userRepository.save(permanentMatchedUser);

        User temporaryMatchedUser = createUser("keywordTemporary");
        temporaryMatchedUser.ban("기간 밴", now.minusDays(2), now.plusDays(3));
        userRepository.save(temporaryMatchedUser);

        User expiredMatchedUser = createUser("keywordExpired");
        expiredMatchedUser.ban("만료된 기간 밴", now.minusDays(5), now.minusDays(1));
        userRepository.save(expiredMatchedUser);

        User permanentUnmatchedUser = createUser("otherPermanent");
        permanentUnmatchedUser.ban("검색어 미일치 영구 밴", now.minusDays(1));
        userRepository.save(permanentUnmatchedUser);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", "keyword")
                        .param("page", "1")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(1))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.counts.total").value(2))
                .andExpect(jsonPath("$.counts.permanent").value(1))
                .andExpect(jsonPath("$.counts.temporary").value(1));
    }

    /**
     * 활성 사용자만 존재할 때 제재 목록과 유형별 집계가 모두 비어 있는지 확인한다.
     */
    @Test
    void emptyBannedUsers() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        createUser("activeUser02");

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.counts.total").value(0))
                .andExpect(jsonPath("$.counts.permanent").value(0))
                .andExpect(jsonPath("$.counts.temporary").value(0));
    }

    /**
     * 영구 제재 상세에 사용자 정보·사유·제재 시점이 포함되고 만료 시점은 비어 있는지 확인한다.
     */
    @Test
    void bannedUserDetail() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        User bannedUser = createUser("bannedDetailUser");
        LocalDateTime bannedAt = LocalDateTime.of(2026, 6, 7, 13, 30);
        bannedUser.ban("반복적인 신고 누적", bannedAt);
        userRepository.save(bannedUser);

        mockMvc.perform(get("/admin/users/banned/{userId}", bannedUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(bannedUser.getId()))
                .andExpect(jsonPath("$.username").value("bannedDetailUser"))
                .andExpect(jsonPath("$.email").value("bannedDetailUser@example.com"))
                .andExpect(jsonPath("$.birthYear").value(1998))
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.country").value("KR"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.banned").value(true))
                .andExpect(jsonPath("$.bannedAt").value("2026-06-07T13:30:00"))
                .andExpect(jsonPath("$.banType").value(UserBanType.PERMANENT.name()))
                .andExpect(jsonPath("$.banExpiresAt").isEmpty())
                .andExpect(jsonPath("$.banReason").value("반복적인 신고 누적"));
    }

    /**
     * 제재되지 않은 사용자는 제재 상세 경로에서 USER_NOT_FOUND로 반환하는지 확인한다.
     */
    @Test
    void detailForActiveUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User activeUser = createUser("activeDetailUser");

        mockMvc.perform(get("/admin/users/banned/{userId}", activeUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /**
     * 7일 제재의 저장 상태·제재 이력·알림 outbox와 감사 로그의 전후 상태를 확인한다.
     */
    @Test
    void temporaryBanSideEffects() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("temporaryBanUser");

        mockMvc.perform(post("/admin/ban/{userId}", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "7일 제재",
                                  "durationDays": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.banned").value(true))
                .andExpect(jsonPath("$.reason").value("7일 제재"))
                .andExpect(jsonPath("$.banType").value(UserBanType.TEMPORARY.name()))
                .andExpect(jsonPath("$.banExpiresAt").isString());

        User persistedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertTrue(persistedUser.isBanned());
        assertEquals(UserBanType.TEMPORARY, persistedUser.getBanType());
        assertNotNull(persistedUser.getBanExpiresAt());

        List<UserSanctionHistory> histories = userSanctionHistoryRepository.findAll();
        assertEquals(1, histories.size());
        UserSanctionHistory history = histories.getFirst();
        assertEquals(UserSanctionAction.APPLIED, history.getAction());
        assertEquals(UserBanType.TEMPORARY, history.getBanType());
        assertEquals("7일 제재", history.getReason());
        assertEquals("adminTester", history.getAdminUsername());
        assertEquals(targetUser.getId(), history.getTargetUserId());
        assertTrue(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:USER_SANCTION:" + history.getId()
        ));

        List<AdminAuditLog> auditLogs = adminAuditLogRepository.findAll();
        assertEquals(1, auditLogs.size());
        AdminAuditLog auditLog = auditLogs.getFirst();
        assertEquals(AdminAuditAction.USER_BAN_APPLIED, auditLog.getAction());
        assertEquals(AdminAuditTargetType.USER, auditLog.getTargetType());
        assertEquals(String.valueOf(targetUser.getId()), auditLog.getTargetId());
        assertEquals("7일 제재", auditLog.getReason());
        assertEquals("adminTester", auditLog.getActorUsername());
        assertTrue(auditLog.getBeforeState().contains("\"banned\":false"));
        assertTrue(auditLog.getAfterState().contains("\"banned\":true"));
    }

    /**
     * 3일 제재 후 상태 조회와 유형·행위 필터를 적용한 이력 조회에서 대상과 관리자 정보를 확인한다.
     */
    @Test
    void sanctionStatusAndHistory() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("historyTargetUser");

        mockMvc.perform(post("/admin/ban/{userId}", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "감사 이력 테스트",
                                  "durationDays": 3
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/users/{userId}/sanction", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.username").value("historyTargetUser"))
                .andExpect(jsonPath("$.banned").value(true))
                .andExpect(jsonPath("$.banType").value(UserBanType.TEMPORARY.name()))
                .andExpect(jsonPath("$.banReason").value("감사 이력 테스트"));

        mockMvc.perform(get("/admin/users/{userId}/sanctions", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20")
                        .param("banType", UserBanType.TEMPORARY.name())
                        .param("action", UserSanctionAction.APPLIED.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(1))
                .andExpect(jsonPath("$.histories[0].targetUserId").value(targetUser.getId()))
                .andExpect(jsonPath("$.histories[0].targetUsername").value("historyTargetUser"))
                .andExpect(jsonPath("$.histories[0].banType").value(UserBanType.TEMPORARY.name()))
                .andExpect(jsonPath("$.histories[0].action").value(UserSanctionAction.APPLIED.name()))
                .andExpect(jsonPath("$.histories[0].reason").value("감사 이력 테스트"))
                .andExpect(jsonPath("$.histories[0].adminUsername").value("adminTester"));
    }

    /**
     * 이력이 없는 기존 사용자는 빈 목록과 현재 계약의 totalPages 1을 반환하는지 확인한다.
     */
    @Test
    void emptySanctionHistory() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("noSanctionHistoryUser");

        mockMvc.perform(get("/admin/users/{userId}/sanctions", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.histories.length()").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    /**
     * 존재하지 않는 사용자의 이력 조회가 USER_NOT_FOUND로 거절되는지 확인한다.
     */
    @Test
    void missingSanctionUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/users/{userId}/sanctions", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /**
     * 역전된 제재 이력 기간에 INVALID_SANCTION_FILTER_PERIOD를 반환하는지 확인한다.
     */
    @Test
    void reversedSanctionPeriod() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("invalidSanctionPeriodUser");

        mockMvc.perform(get("/admin/users/{userId}/sanctions", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "5")
                        .param("from", "2026-06-30T00:00:00")
                        .param("to", "2026-06-01T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SANCTION_FILTER_PERIOD"));
    }

    /**
     * 제재 해제가 상태 조회에 반영되고 적용·해제 이력 두 건과 해제 감사·알림 요청이 남는지 확인한다.
     */
    @Test
    void releaseBanSideEffects() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("releaseTargetUser");

        mockMvc.perform(post("/admin/ban/{userId}", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "해제 테스트"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/ban/{userId}/release", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "운영 검토 결과 해제"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.banned").value(false))
                .andExpect(jsonPath("$.reason").value("운영 검토 결과 해제"));

        User persistedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertFalse(persistedUser.isBanned());
        assertEquals(2, userSanctionHistoryRepository.findAll().size());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.USER_BAN_RELEASED
                        && log.getTargetId().equals(String.valueOf(targetUser.getId()))));
        UserSanctionHistory releasedHistory = userSanctionHistoryRepository.findAll().stream()
                .filter(history -> history.getAction() == UserSanctionAction.RELEASED)
                .findFirst()
                .orElseThrow();
        assertTrue(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:USER_SANCTION:" + releasedHistory.getId()
        ));

        mockMvc.perform(get("/admin/users/{userId}/sanction", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banned").value(false));
    }

    /**
     * 비제재 사용자 해제 요청이 USER_NOT_BANNED로 충돌하는지 확인한다.
     */
    @Test
    void releaseUnbannedUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("notBannedUser");

        mockMvc.perform(post("/admin/ban/{userId}/release", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "잘못된 해제 요청"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_NOT_BANNED"));
    }

    /**
     * 만료된 임시 제재를 상태 조회 중 해제하고 EXPIRED 이력 및 알림 outbox를 남기는지 확인한다.
     */
    @Test
    void expireBanOnStatusRead() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User targetUser = createUser("expiredTemporaryBanUser");
        LocalDateTime now = LocalDateTime.now();
        targetUser.ban("만료 정리 테스트", now.minusDays(2), now.minusDays(1));
        userRepository.saveAndFlush(targetUser);

        mockMvc.perform(get("/admin/users/{userId}/sanction", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUser.getId()))
                .andExpect(jsonPath("$.banned").value(false));

        User persistedUser = userRepository.findById(targetUser.getId()).orElseThrow();
        assertFalse(persistedUser.isBanned());

        List<UserSanctionHistory> histories = userSanctionHistoryRepository.findAll();
        assertEquals(1, histories.size());
        UserSanctionHistory history = histories.getFirst();
        assertEquals(UserSanctionAction.EXPIRED, history.getAction());
        assertEquals(UserBanType.TEMPORARY, history.getBanType());
        assertEquals("만료 정리 테스트", history.getReason());
        assertTrue(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:USER_SANCTION:" + history.getId()
        ));
    }

    /**
     * 관리자 식별자가 없는 제재 서비스 호출이 AuthException으로 거절되는지 확인한다.
     */
    @Test
    void applyBanRejectsNullAdminUserId() {
        User targetUser = createUser("nullAdminBanUser");

        assertThrows(AuthException.class, () -> userSanctionCommandService.applyBan(
                targetUser,
                "관리자 정보 누락",
                LocalDateTime.now(),
                null,
                null
        ));
    }

    /**
     * 제재 대상으로 사용할 일반 사용자를 암호화된 비밀번호와 함께 저장한다.
     */
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

    /**
     * 기본 SUPER_ADMIN인 adminTester의 접근 토큰을 반환한다.
     */
    private String createAdminAndLogin() throws Exception {
        return createAdminAndLogin("adminTester", AdminRole.SUPER_ADMIN);
    }

    /**
     * 관리자를 저장하고 역할이 null이 아닐 때만 배정하여 미배정 권한 경계도 구성한다.
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
        if (adminRole != null) {
            adminRoleAssignmentRepository.save(AdminRoleAssignment.assign(
                    admin.getId(), adminRole, admin.getId(), LocalDateTime.now()
            ));
        }

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
