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
import software.amazon.awssdk.services.s3.S3Client;

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

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        adminAuditLogRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
        postReportRepository.deleteAllInBatch();
        reporterModerationPolicyRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void acceptReportMarksAcceptedAndBansReportedUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
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

    @Test
    void declineReportMarksDeclinedWithoutBanningUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
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

    @Test
    void acceptPostReportsProcessesAllPendingReportsAndBansReportedUserOnce() throws Exception {
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

    @Test
    void declinePostReportsProcessesAllPendingReportsWithoutHidingOrBanningUser() throws Exception {
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

    @Test
    void acceptPostReportsFailsWhenNoPendingReportExists() throws Exception {
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

    @Test
    void acceptReportFailsWhenReportAlreadyProcessed() throws Exception {
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

    @Test
    void reportedUsersSearchEscapesLikeWildcards() throws Exception {
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
        String username = "adminTester" + System.nanoTime();
        userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.ADMIN)
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

    private MapImage createMapImage(Long userId, String imageUrl) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl(imageUrl)
                .s3Key("test-key-" + userId)
                .title("신고 대상 제목")
                .description("신고 대상 설명")
                .userId(userId)
                .build());
    }

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
