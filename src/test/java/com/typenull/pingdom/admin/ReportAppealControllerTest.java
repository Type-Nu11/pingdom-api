package com.typenull.pingdom.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.moderation.domain.appeal.ReportAppealStatus;
import com.typenull.pingdom.moderation.domain.audit.AdminAuditAction;
import com.typenull.pingdom.moderation.infrastructure.persistence.AdminAuditLogRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.ReportAppealRepository;
import com.typenull.pingdom.moderation.infrastructure.persistence.UserSanctionHistoryRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
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
class ReportAppealControllerTest {

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
    private ReportAppealRepository reportAppealRepository;

    @Autowired
    private UserSanctionHistoryRepository userSanctionHistoryRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAllInBatch();
        userSanctionHistoryRepository.deleteAllInBatch();
        reportAppealRepository.deleteAllInBatch();
        postReportRepository.deleteAllInBatch();
        reporterModerationPolicyRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void bannedUserCanSubmitAppealAndAdminApprovalRestoresPostAndBan() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("appealOwner", UserRole.USER);
        User reporter = createUser("appealReporter", UserRole.USER);
        String ownerAccessToken = login(owner.getUsername());
        MapImage mapImage = createMapImage(owner.getId(), owner.getUsername());
        PostReport postReport = createPostReport(reporter, mapImage, "오처리 신고");

        mockMvc.perform(post("/admin/reports/{id}/accept", postReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        assertEquals(MapImageVisibilityStatus.AUTO_HIDDEN,
                mapImageRepository.findById(mapImage.getId()).orElseThrow().getVisibilityStatus());
        assertTrue(userRepository.findById(owner.getId()).orElseThrow().isCurrentlyBanned(LocalDateTime.now()));

        MvcResult appealResult = mockMvc.perform(post("/map/report-appeals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": %d,
                                  "reason": "정상 게시글입니다."
                                }
                                """.formatted(postReport.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(ReportAppealStatus.SUBMITTED.name()))
                .andReturn();
        Long appealId = objectMapper.readTree(appealResult.getResponse().getContentAsString())
                .get("appealId")
                .asLong();

        mockMvc.perform(post("/admin/report-appeals/{id}/approve", appealId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "오처리 확인"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReportAppealStatus.APPROVED.name()));

        MapImage restoredImage = mapImageRepository.findById(mapImage.getId()).orElseThrow();
        User restoredOwner = userRepository.findById(owner.getId()).orElseThrow();
        PostReport restoredReport = postReportRepository.findById(postReport.getId()).orElseThrow();

        assertEquals(MapImageVisibilityStatus.ACTIVE, restoredImage.getVisibilityStatus());
        assertFalse(restoredOwner.isCurrentlyBanned(LocalDateTime.now()));
        assertEquals(PostReportStatus.RESTORED, restoredReport.getStatus());
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.APPEAL_APPROVED));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.POST_RESTORED));
        assertTrue(adminAuditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AdminAuditAction.USER_BAN_RELEASED));
    }

    @Test
    void approveAppealKeepsBanWhenAnotherAcceptedReportExists() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("multiAppealOwner", UserRole.USER);
        User firstReporter = createUser("firstAppealReporter", UserRole.USER);
        User secondReporter = createUser("secondAppealReporter", UserRole.USER);
        String ownerAccessToken = login(owner.getUsername());
        MapImage firstImage = createMapImage(owner.getId(), owner.getUsername());
        MapImage secondImage = createMapImage(owner.getId(), owner.getUsername());
        PostReport firstReport = createPostReport(firstReporter, firstImage, "첫 번째 신고");
        PostReport secondReport = createPostReport(secondReporter, secondImage, "두 번째 신고");

        mockMvc.perform(post("/admin/reports/{id}/accept", firstReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());
        secondReport.accept(LocalDateTime.now());
        postReportRepository.saveAndFlush(secondReport);

        MvcResult appealResult = mockMvc.perform(post("/map/report-appeals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": %d,
                                  "reason": "첫 번째 신고는 오처리입니다."
                                }
                                """.formatted(firstReport.getId())))
                .andExpect(status().isCreated())
                .andReturn();
        Long appealId = objectMapper.readTree(appealResult.getResponse().getContentAsString())
                .get("appealId")
                .asLong();

        mockMvc.perform(post("/admin/report-appeals/{id}/approve", appealId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "첫 번째 신고 오처리 확인"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReportAppealStatus.APPROVED.name()));

        assertTrue(userRepository.findById(owner.getId()).orElseThrow().isCurrentlyBanned(LocalDateTime.now()));
        assertEquals(PostReportStatus.RESTORED, postReportRepository.findById(firstReport.getId()).orElseThrow().getStatus());
        assertEquals(PostReportStatus.ACCEPTED, postReportRepository.findById(secondReport.getId()).orElseThrow().getStatus());
    }

    @Test
    void approveAllAppealsReleasesBanEvenWhenLastRestoredReportDiffersFromCurrentBanReason() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("sequentialAppealOwner", UserRole.USER);
        User firstReporter = createUser("sequentialFirstReporter", UserRole.USER);
        User secondReporter = createUser("sequentialSecondReporter", UserRole.USER);
        String ownerAccessToken = login(owner.getUsername());
        MapImage firstImage = createMapImage(owner.getId(), owner.getUsername());
        MapImage secondImage = createMapImage(owner.getId(), owner.getUsername());
        PostReport firstReport = createPostReport(firstReporter, firstImage, "첫 번째 신고");
        PostReport secondReport = createPostReport(secondReporter, secondImage, "두 번째 신고");

        mockMvc.perform(post("/admin/reports/{id}/accept", firstReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/reports/{id}/accept", secondReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk());

        Long firstAppealId = submitAppeal(ownerAccessToken, firstReport.getId(), "첫 번째 신고는 오처리입니다.");
        Long secondAppealId = submitAppeal(ownerAccessToken, secondReport.getId(), "두 번째 신고는 오처리입니다.");

        mockMvc.perform(post("/admin/report-appeals/{id}/approve", secondAppealId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "두 번째 신고 오처리 확인"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReportAppealStatus.APPROVED.name()));
        assertTrue(userRepository.findById(owner.getId()).orElseThrow().isCurrentlyBanned(LocalDateTime.now()));

        mockMvc.perform(post("/admin/report-appeals/{id}/approve", firstAppealId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "첫 번째 신고 오처리 확인"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(ReportAppealStatus.APPROVED.name()));

        assertFalse(userRepository.findById(owner.getId()).orElseThrow().isCurrentlyBanned(LocalDateTime.now()));
        assertEquals(PostReportStatus.RESTORED, postReportRepository.findById(firstReport.getId()).orElseThrow().getStatus());
        assertEquals(PostReportStatus.RESTORED, postReportRepository.findById(secondReport.getId()).orElseThrow().getStatus());
    }

    private Long submitAppeal(String accessToken, Long reportId, String reason) throws Exception {
        MvcResult appealResult = mockMvc.perform(post("/map/report-appeals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reportId": %d,
                                  "reason": "%s"
                                }
                                """.formatted(reportId, reason)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(appealResult.getResponse().getContentAsString())
                .get("appealId")
                .asLong();
    }

    private User createUser(String username, UserRole role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(role)
                .build());
    }

    private String createAdminAndLogin() throws Exception {
        createUser("appealAdmin", UserRole.ADMIN);
        return login("appealAdmin");
    }

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

    private MapImage createMapImage(Long userId, String username) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/appeal-image.jpg")
                .s3Key("appeal-image-key")
                .title("이의제기 대상")
                .description("이의제기 대상 설명")
                .userId(userId)
                .username(username)
                .build());
    }

    private PostReport createPostReport(User reporter, MapImage mapImage, String reason) {
        return postReportRepository.save(PostReport.builder()
                .reporterUserId(reporter.getId())
                .reporterUsername(reporter.getUsername())
                .reportedImageId(mapImage.getId())
                .reportedUserId(mapImage.getUserId())
                .reportedImageUrl(mapImage.getImageUrl())
                .mapImage(mapImage)
                .reason(reason)
                .build());
    }
}
