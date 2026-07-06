package com.typenull.pingdom.admin;

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
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.application.service.UserSanctionCommandService;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditLog;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionHistory;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
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
    private UserSanctionHistoryRepository userSanctionHistoryRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private UserSanctionCommandService userSanctionCommandService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
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

    @Test
    void listBannedUsersFiltersByKeywordForUserIdAndUsername() throws Exception {
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

    @Test
    void listBannedUsersTreatsNumericKeywordAsExactUserIdOnly() throws Exception {
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

    @Test
    void listBannedUsersFiltersByBanTypePeriodAndSort() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        User permanentUser = createUser("permanentUser");
        permanentUser.ban("영구 밴", LocalDateTime.of(2026, 6, 2, 9, 0));
        userRepository.save(permanentUser);

        User temporaryUserEarly = createUser("temporaryUserA");
        temporaryUserEarly.ban(
                "기간 밴 A",
                LocalDateTime.of(2026, 6, 4, 9, 0),
                LocalDateTime.of(2026, 7, 10, 9, 0)
        );
        userRepository.save(temporaryUserEarly);

        User temporaryUserLate = createUser("temporaryUserB");
        temporaryUserLate.ban(
                "기간 밴 B",
                LocalDateTime.of(2026, 6, 6, 9, 0),
                LocalDateTime.of(2026, 7, 12, 9, 0)
        );
        userRepository.save(temporaryUserLate);

        mockMvc.perform(get("/admin/users/banned")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("banType", UserBanType.TEMPORARY.name())
                        .param("bannedFrom", "2026-06-03T00:00:00")
                        .param("bannedTo", "2026-06-06T23:59:59")
                        .param("sortBy", "EXPIRES_AT")
                        .param("sortDirection", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(2))
                .andExpect(jsonPath("$.users[0].userId").value(temporaryUserEarly.getId()))
                .andExpect(jsonPath("$.users[0].banType").value(UserBanType.TEMPORARY.name()))
                .andExpect(jsonPath("$.users[0].banExpiresAt").value("2026-07-10T09:00:00"))
                .andExpect(jsonPath("$.users[1].userId").value(temporaryUserLate.getId()))
                .andExpect(jsonPath("$.totalCount").value(2));
    void listBannedUsersReturnsCountsForCurrentBannedUsersWithKeywordApplied() throws Exception {
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

    @Test
    void listBannedUsersReturnsEmptyListWhenNoBannedUserExists() throws Exception {
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

    @Test
    void getBannedUserReturnsUserDetail() throws Exception {
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

    @Test
    void getBannedUserReturnsNotFoundWhenUserIsNotBanned() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User activeUser = createUser("activeDetailUser");

        mockMvc.perform(get("/admin/users/banned/{userId}", activeUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void banUserAppliesTemporaryBanAndStoresHistory() throws Exception {
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

    @Test
    void getSanctionStatusAndHistoryReturnsAuditInfo() throws Exception {
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

    @Test
    void unbanUserReleasesCurrentBanAndStoresHistory() throws Exception {
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

        mockMvc.perform(get("/admin/users/{userId}/sanction", targetUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banned").value(false));
    }

    @Test
    void unbanUserReturnsConflictWhenUserIsNotBanned() throws Exception {
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

    @Test
    void getSanctionStatusExpiresTemporaryBanAndStoresHistory() throws Exception {
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
    }

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
