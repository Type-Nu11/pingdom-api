package com.typenull.pingdom.integration.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.admin.AdminRole;
import com.typenull.pingdom.identity.domain.admin.AdminRoleAssignment;
import com.typenull.pingdom.identity.domain.repository.AdminRoleAssignmentRepository;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditTargetType;
import com.typenull.pingdom.moderation.domain.sanction.UserSanctionAction;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 신고 처리 권한 및 단일·일괄 승인/기각의 게시물·제재·통계·감사 부작용을 검증한다.
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
        "fcm.key-path=dummy"
})
@AutoConfigureMockMvc
class AdminReportControllerTest {

    @MockBean
    private S3Client s3Client;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminRoleAssignmentRepository adminRoleAssignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private ReporterModerationPolicyRepository reporterModerationPolicyRepository;

    @Autowired
    private UserSanctionHistoryRepository userSanctionHistoryRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * outbox·감사·제재·신고·신고자 정책을 게시물과 사용자보다 먼저 삭제해 처리 부작용을 격리한다.
     */
    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        adminAuditLogRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
        postReportRepository.deleteAllInBatch();
        reporterModerationPolicyRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        adminRoleAssignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 단일·일괄 신고 처리에 권한이 없는 역할 조합과 승인만 금지된 CONTENT_MODERATOR 조합을 제공한다.
     */
    static Stream<Arguments> deniedReportActions() {
        return Stream.concat(
                Stream.of("ANALYST", "NO_ROLE", "REVOKED", "SUPPORT_OPERATOR")
                        .flatMap(role -> Stream.of("accept", "decline", "bulk-accept", "bulk-decline")
                                .map(action -> Arguments.of(role, action))),
                Stream.of("accept", "bulk-accept").map(action -> Arguments.of("CONTENT_MODERATOR", action))
        );
    }

    /**
     * 권한 거절 뒤 신고·게시물·사용자 상태와 감사·outbox·제재·신고자 정책 건수가 모두 유지되는지 확인한다.
     */
    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("deniedReportActions")
    void deniedActionPreservesState(String role, String action) throws Exception {
        String token = createAdminAndLogin(role);
        User owner = createUser("denied-owner");
        User reporter = createUser("denied-reporter");
        MapImage image = createMapImage(owner.getId(), "https://example.com/denied.jpg");
        PostReport report = createPostReport(reporter.getId(), reporter.getUsername(), image, "reason");
        long auditCount = adminAuditLogRepository.count();
        long outboxCount = outboxEventRepository.count();
        long sanctionCount = userSanctionHistoryRepository.count();
        long policyCount = reporterModerationPolicyRepository.count();
        String endpoint = action.startsWith("bulk-")
                ? "/admin/posts/" + image.getId() + "/reports/" + action.substring(5)
                : "/admin/reports/" + report.getId() + "/" + action;

        mockMvc.perform(post(endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PERMISSION_REQUIRED"));

        assertEquals(PostReportStatus.PENDING, postReportRepository.findById(report.getId()).orElseThrow().getStatus());
        assertFalse(userRepository.findById(owner.getId()).orElseThrow().isBanned());
        assertTrue(mapImageRepository.findById(image.getId()).orElseThrow().isVisible());
        assertEquals(reporter.getReportCount(), userRepository.findById(reporter.getId()).orElseThrow().getReportCount());
        assertEquals(auditCount, adminAuditLogRepository.count());
        assertEquals(outboxCount, outboxEventRepository.count());
        assertEquals(sanctionCount, userSanctionHistoryRepository.count());
        assertEquals(policyCount, reporterModerationPolicyRepository.count());
    }

    /**
     * 승인 가능한 역할에서 신고 수락, 작성자 제재, 게시물 자동 숨김과 감사·관리자 알림 outbox를 확인한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "CONTENT_AND_SUPPORT"})
    void acceptReportSideEffects(String role) throws Exception {
        String adminAccessToken = createAdminAndLogin(role);
        User owner = createUser("owner03");
        User reporter = createUser("reporter03");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-3.jpg");
        PostReport postReport = createPostReport(
                reporter.getId(),
                reporter.getUsername(),
                mapImage,
                "욕설이 포함된 이미지입니다."
        );

        mockMvc.perform(post("/admin/reports/{id}/accept", postReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(postReport.getId()))
                .andExpect(jsonPath("$.status").value(PostReportStatus.ACCEPTED.name()))
                .andExpect(jsonPath("$.reportedUserId").value(owner.getId()))
                .andExpect(jsonPath("$.banned").value(true));

        PostReport persistedReport = postReportRepository.findById(postReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();

        assertEquals(PostReportStatus.ACCEPTED, persistedReport.getStatus());
        assertTrue(persistedOwner.isBanned());
        assertEquals("욕설이 포함된 이미지입니다.", persistedOwner.getBanReason());
        MapImage persistedImage = mapImageRepository.findById(mapImage.getId()).orElseThrow();
        assertEquals(MapImageVisibilityStatus.AUTO_HIDDEN, persistedImage.getVisibilityStatus());
        assertEquals(UserSanctionAction.APPLIED, userSanctionHistoryRepository.findAll().getFirst().getAction());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.REPORT_ACCEPTED
                        && log.getTargetType() == AdminAuditTargetType.REPORT
                        && log.getTargetId().equals(String.valueOf(postReport.getId()))));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.POST_HIDDEN
                        && log.getTargetType() == AdminAuditTargetType.POST
                        && log.getTargetId().equals(String.valueOf(mapImage.getId()))));
        assertTrue(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:REPORT_PROCESSED:" + postReport.getId()
        ));
        Long sanctionId = userSanctionHistoryRepository.findAll().getFirst().getId();
        assertTrue(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:USER_SANCTION:" + sanctionId
        ));
    }

    /**
     * 기각 가능한 역할에서 신고만 기각하고 작성자는 제재하지 않으며 처리 감사와 알림 요청을 남기는지 확인한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"SUPER_ADMIN", "CONTENT_MODERATOR"})
    void declineReportSideEffects(String role) throws Exception {
        String adminAccessToken = createAdminAndLogin(role);
        User owner = createUser("owner04");
        User reporter = createUser("reporter04");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-4.jpg");
        PostReport postReport = createPostReport(
                reporter.getId(),
                reporter.getUsername(),
                mapImage,
                "잘못된 위치 정보입니다."
        );

        mockMvc.perform(post("/admin/reports/{id}/decline", postReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(PostReportStatus.DECLINED.name()))
                .andExpect(jsonPath("$.banned").value(false));

        PostReport persistedReport = postReportRepository.findById(postReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();

        assertEquals(PostReportStatus.DECLINED, persistedReport.getStatus());
        assertTrue(!persistedOwner.isBanned());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.REPORT_DECLINED
                        && log.getTargetId().equals(String.valueOf(postReport.getId()))));
        assertTrue(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:REPORT_PROCESSED:" + postReport.getId()
        ));
    }

    /**
     * 대기 신고 두 건을 수락하고 게시물 숨김·작성자 제재 한 건·신고자별 승인 통계·감사 기록을 확인한다.
     */
    @Test
    void bulkAcceptReports() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("bulkOwner01");
        User reporter1 = createUser("bulkReporter01");
        User reporter2 = createUser("bulkReporter02");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/bulk-image-1.jpg");
        PostReport firstReport = createPostReport(
                reporter1.getId(),
                reporter1.getUsername(),
                mapImage,
                "부적절한 이미지입니다."
        );
        PostReport secondReport = createPostReport(
                reporter2.getId(),
                reporter2.getUsername(),
                mapImage,
                "욕설이 포함된 이미지입니다."
        );

        mockMvc.perform(post("/admin/posts/{postId}/reports/accept", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(mapImage.getId()))
                .andExpect(jsonPath("$.status").value(PostReportStatus.ACCEPTED.name()))
                .andExpect(jsonPath("$.processedReportCount").value(2))
                .andExpect(jsonPath("$.visibilityStatus").value(MapImageVisibilityStatus.AUTO_HIDDEN.name()))
                .andExpect(jsonPath("$.hiddenAt").exists())
                .andExpect(jsonPath("$.hiddenReason").value("REPORT_BULK_ACCEPTED"))
                .andExpect(jsonPath("$.processedAt").exists());

        PostReport persistedFirstReport = postReportRepository.findById(firstReport.getId()).orElseThrow();
        PostReport persistedSecondReport = postReportRepository.findById(secondReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();
        MapImage persistedImage = mapImageRepository.findById(mapImage.getId()).orElseThrow();

        assertEquals(PostReportStatus.ACCEPTED, persistedFirstReport.getStatus());
        assertEquals(PostReportStatus.ACCEPTED, persistedSecondReport.getStatus());
        assertTrue(persistedOwner.isBanned());
        assertEquals("REPORT_BULK_ACCEPTED", persistedOwner.getBanReason());
        assertEquals(MapImageVisibilityStatus.AUTO_HIDDEN, persistedImage.getVisibilityStatus());
        assertEquals(1, userSanctionHistoryRepository.findAll().size());
        assertEquals(1L, reporterModerationPolicyRepository.findById(reporter1.getId()).orElseThrow().getAcceptedCount());
        assertEquals(1L, reporterModerationPolicyRepository.findById(reporter2.getId()).orElseThrow().getAcceptedCount());
        assertEquals(2, adminAuditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.REPORT_ACCEPTED
                        && log.getTargetType() == AdminAuditTargetType.REPORT)
                .count());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.POST_HIDDEN
                        && log.getTargetType() == AdminAuditTargetType.POST
                        && log.getTargetId().equals(String.valueOf(mapImage.getId()))));
    }

    /**
     * 대기 신고 두 건을 기각하면서 게시물 노출과 작성자 비제재 상태를 보존하고 신고자 통계와 감사를 확인한다.
     */
    @Test
    void bulkDeclineReports() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("bulkOwner02");
        User reporter1 = createUser("bulkReporter03");
        User reporter2 = createUser("bulkReporter04");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/bulk-image-2.jpg");
        PostReport firstReport = createPostReport(
                reporter1.getId(),
                reporter1.getUsername(),
                mapImage,
                "위치가 맞지 않습니다."
        );
        PostReport secondReport = createPostReport(
                reporter2.getId(),
                reporter2.getUsername(),
                mapImage,
                "문제가 없는 게시글을 신고합니다."
        );

        mockMvc.perform(post("/admin/posts/{postId}/reports/decline", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(mapImage.getId()))
                .andExpect(jsonPath("$.status").value(PostReportStatus.DECLINED.name()))
                .andExpect(jsonPath("$.processedReportCount").value(2))
                .andExpect(jsonPath("$.visibilityStatus").value(MapImageVisibilityStatus.ACTIVE.name()))
                .andExpect(jsonPath("$.hiddenAt").doesNotExist())
                .andExpect(jsonPath("$.hiddenReason").doesNotExist())
                .andExpect(jsonPath("$.processedAt").exists());

        PostReport persistedFirstReport = postReportRepository.findById(firstReport.getId()).orElseThrow();
        PostReport persistedSecondReport = postReportRepository.findById(secondReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();
        MapImage persistedImage = mapImageRepository.findById(mapImage.getId()).orElseThrow();

        assertEquals(PostReportStatus.DECLINED, persistedFirstReport.getStatus());
        assertEquals(PostReportStatus.DECLINED, persistedSecondReport.getStatus());
        assertFalse(persistedOwner.isBanned());
        assertEquals(MapImageVisibilityStatus.ACTIVE, persistedImage.getVisibilityStatus());
        assertTrue(userSanctionHistoryRepository.findAll().isEmpty());
        assertEquals(1L, reporterModerationPolicyRepository.findById(reporter1.getId()).orElseThrow().getDeclinedCount());
        assertEquals(1L, reporterModerationPolicyRepository.findById(reporter2.getId()).orElseThrow().getDeclinedCount());
        assertEquals(2, adminAuditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AdminAuditAction.REPORT_DECLINED
                        && log.getTargetType() == AdminAuditTargetType.REPORT)
                .count());
    }

    /**
     * 기각된 신고만 있는 게시물의 일괄 승인이 PENDING_REPORT_NOT_FOUND로 충돌하는지 확인한다.
     */
    @Test
    void bulkAcceptWithoutPending() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("bulkOwner03");
        User reporter = createUser("bulkReporter05");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/bulk-image-3.jpg");
        PostReport postReport = createPostReport(
                reporter.getId(),
                reporter.getUsername(),
                mapImage,
                "이미 처리된 신고입니다."
        );
        postReport.decline(java.time.LocalDateTime.now());
        postReportRepository.save(postReport);

        mockMvc.perform(post("/admin/posts/{postId}/reports/accept", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_REPORT_NOT_FOUND"));
    }

    /**
     * 이미 기각된 신고를 승인하면 REPORT_ALREADY_PROCESSED로 충돌하는지 확인한다.
     */
    @Test
    void acceptProcessedReport() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner05");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-5.jpg");
        PostReport postReport = createPostReport(15L, "reporter05", mapImage, "중복 이미지입니다.");
        postReport.decline(java.time.LocalDateTime.now());
        postReportRepository.save(postReport);

        mockMvc.perform(post("/admin/reports/{id}/accept", postReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_PROCESSED"));
    }

    /**
     * 검색어 %가 LIKE 전체 일치로 확장되지 않고 사유에 실제 %가 있는 신고만 찾는지 확인한다.
     */
    @Test
    void literalWildcardSearch() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User firstOwner = createUser("ownerWildcard01");
        User secondOwner = createUser("ownerWildcard02");
        MapImage firstImage = createMapImage(firstOwner.getId(), "https://example.com/image-wildcard-1.jpg");
        MapImage secondImage = createMapImage(secondOwner.getId(), "https://example.com/image-wildcard-2.jpg");
        createPostReport(16L, "reporterWildcard01", firstImage, "문자 % 포함 신고입니다.");
        createPostReport(17L, "reporterWildcard02", secondImage, "일반 신고입니다.");

        mockMvc.perform(get("/admin/reports/reported-users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.users[0].reason").value("문자 % 포함 신고입니다."));
    }

    /**
     * 게시물 소유자 또는 신고자로 사용할 일반 사용자를 저장한다.
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
     * 기본 SUPER_ADMIN 권한의 로그인 토큰을 만든다.
     */
    private String createAdminAndLogin() throws Exception {
        return createAdminAndLogin("SUPER_ADMIN");
    }

    /**
     * 복합 역할·미배정·회수 상태를 포함한 관리자 fixture를 저장하고 실제 로그인 토큰을 반환한다.
     */
    private String createAdminAndLogin(String role) throws Exception {
        String username = "adminTester" + System.nanoTime();
        User admin = userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
                .build());
        if (role.equals("CONTENT_AND_SUPPORT")) {
            adminRoleAssignmentRepository.save(AdminRoleAssignment.assign(
                    admin.getId(), AdminRole.SUPPORT_OPERATOR, admin.getId(), LocalDateTime.now()
            ));
            role = "CONTENT_MODERATOR";
        }
        if (!role.equals("NO_ROLE")) {
            AdminRoleAssignment assignment = AdminRoleAssignment.assign(
                    admin.getId(), role.equals("REVOKED") ? AdminRole.SUPER_ADMIN : AdminRole.valueOf(role),
                    admin.getId(), LocalDateTime.now()
            );
            if (role.equals("REVOKED")) {
                assignment.revoke(LocalDateTime.now());
            }
            adminRoleAssignmentRepository.save(assignment);
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

    /**
     * 소유자 ID와 삭제용 S3 키를 가진 신고 대상 게시물을 저장한다.
     */
    private MapImage createMapImage(Long userId, String imageUrl) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl(imageUrl)
                .s3Key("test-key-" + userId)
                .title("신고 대상 제목")
                .description("신고 대상 설명")
                .userId(userId)
                .build());
    }

    /**
     * 게시물 연관관계와 신고 당시 작성자·이미지 URL을 함께 저장해 처리 대상 신고를 만든다.
     */
    private PostReport createPostReport(Long reporterUserId, String reporterUsername, MapImage mapImage, String reason) {
        return postReportRepository.save(PostReport.builder()
                .reporterUserId(reporterUserId)
                .reporterUsername(reporterUsername)
                .reportedImageId(mapImage.getId())
                .reportedUserId(mapImage.getUserId())
                .reportedImageUrl(mapImage.getImageUrl())
                .mapImage(mapImage)
                .reason(reason)
                .build());
    }
}
