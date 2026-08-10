package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.policy.ReporterModerationPolicy;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.engagement.infrastructure.persistence.ReporterModerationPolicyRepository;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.aws.s3.bucket=test-bucket",
        "spring.cloud.aws.region.static=ap-northeast-2",
        "spring.cloud.aws.credentials.access-key=test-access-key",
        "spring.cloud.aws.credentials.secret-key=test-secret-key"
})
@AutoConfigureMockMvc
class PostReportControllerTest {

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
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        postReportRepository.deleteAllInBatch();
        reporterModerationPolicyRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void reportRegistersPostReport() throws Exception {
        String accessToken = signupAndLogin("reporter01");
        MapImage mapImage = createMapImage(101L);

        mockMvc.perform(post("/map/posts/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "부적절한 사진입니다."
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(1L, postReportRepository.count());
        PostReport postReport = postReportRepository.findAll().get(0);
        assertEquals("reporter01", postReport.getReporterUsername());
        assertEquals("부적절한 사진입니다.", postReport.getReason());
        assertEquals(mapImage.getId(), postReport.getMapImage().getId());
        org.assertj.core.api.Assertions.assertThat(outboxEventRepository.existsByDeduplicationKey(
                "ADMIN_NOTIFICATION:REPORT_RECEIVED:" + postReport.getId()
        )).isTrue();
    }

    @Test
    void reportLegacyAliasStillWorks() throws Exception {
        String accessToken = signupAndLogin("reporter-legacy");
        MapImage mapImage = createMapImage(106L);

        mockMvc.perform(post("/map/post/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "레거시 신고 경로 테스트"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void reportSucceedsWithSameTokenThatCanAccessMyPage() throws Exception {
        String accessToken = signupAndLogin("reporter05");
        MapImage mapImage = createMapImage(105L);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/map/posts/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "같은 토큰으로 신고 테스트"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void reportFailsWhenUserReportsSamePostTwice() throws Exception {
        String accessToken = signupAndLogin("reporter02");
        MapImage mapImage = createMapImage(102L);

        mockMvc.perform(post("/map/posts/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "중복 신고 테스트"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/posts/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "다시 신고합니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED_IMAGE"));
    }

    @Test
    void reportFailsWhenPostDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("reporter03");

        mockMvc.perform(post("/map/posts/{id}/report", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "없는 사진입니다."
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
    }

    @Test
    void reportFailsWhenReasonIsBlank() throws Exception {
        String accessToken = signupAndLogin("reporter04");
        MapImage mapImage = createMapImage(104L);

        mockMvc.perform(post("/map/posts/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").value("신고 사유는 필수입니다."));
    }

    @Test
    void reportAutoHidesPostWhenTrustedReportsReachThreshold() throws Exception {
        String firstToken = signupAndLogin("reporterAuto01");
        String secondToken = signupAndLogin("reporterAuto02");
        String thirdToken = signupAndLogin("reporterAuto03");
        MapImage mapImage = createMapImage(201L);

        reportPost(firstToken, mapImage.getId(), "첫 번째 신고");
        reportPost(secondToken, mapImage.getId(), "두 번째 신고");
        reportPost(thirdToken, mapImage.getId(), "세 번째 신고");

        MapImage persistedImage = mapImageRepository.findById(mapImage.getId()).orElseThrow();
        assertEquals(MapImageVisibilityStatus.AUTO_HIDDEN, persistedImage.getVisibilityStatus());
        assertEquals(3L, postReportRepository.count());
    }

    @Test
    void reportFailsWhenReporterIsRestrictedByFalseReportPolicy() throws Exception {
        User reporter = signupAndFindUser("restrictedReporter");
        String accessToken = login("restrictedReporter");
        reporterModerationPolicyRepository.save(ReporterModerationPolicy.builder()
                .reporterUserId(reporter.getId())
                .reporterUsername(reporter.getUsername())
                .falseReportCount(3)
                .trustScore(40)
                .restrictedUntil(LocalDateTime.now().plusDays(1))
                .restrictionReason("FALSE_REPORT_THRESHOLD_EXCEEDED")
                .build());
        MapImage mapImage = createMapImage(202L);

        mockMvc.perform(post("/map/posts/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "제한 중 신고"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REPORTER_RESTRICTED"));
    }

    @Test
    void listMyReportsReturnsCurrentUsersReportsInLatestReportOrder() throws Exception {
        String reporterToken = signupAndLogin("my-report-reader");
        String otherReporterToken = signupAndLogin("other-report-reader");
        MapImage olderReportedPost = createMapImage(301L, "먼저 신고한 게시글");
        MapImage latestReportedPost = createMapImage(302L, "나중에 신고한 게시글");
        MapImage otherReportedPost = createMapImage(303L, "다른 사용자가 신고한 게시글");

        reportPost(reporterToken, olderReportedPost.getId(), "먼저 신고한 사유");
        reportPost(otherReporterToken, otherReportedPost.getId(), "다른 사용자 신고 사유");
        reportPost(reporterToken, latestReportedPost.getId(), "나중에 신고한 사유");

        PostReport olderReport = postReportRepository.findAll().stream()
                .filter(report -> report.getReportedImageId().equals(olderReportedPost.getId()))
                .findFirst()
                .orElseThrow();
        olderReport.accept(LocalDateTime.now());
        postReportRepository.saveAndFlush(olderReport);

        mockMvc.perform(get("/map/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.reports.length()").value(2))
                .andExpect(jsonPath("$.reports[0].postId").value(latestReportedPost.getId()))
                .andExpect(jsonPath("$.reports[0].title").value("나중에 신고한 게시글"))
                .andExpect(jsonPath("$.reports[0].reason").value("나중에 신고한 사유"))
                .andExpect(jsonPath("$.reports[0].status").value("PENDING"))
                .andExpect(jsonPath("$.reports[1].postId").value(olderReportedPost.getId()))
                .andExpect(jsonPath("$.reports[1].title").value("먼저 신고한 게시글"))
                .andExpect(jsonPath("$.reports[1].reason").value("먼저 신고한 사유"))
                .andExpect(jsonPath("$.reports[1].status").value("ACCEPTED"));
    }

    private String signupAndLogin(String username) throws Exception {
        signup(username);
        return login(username);
    }

    private User signupAndFindUser(String username) throws Exception {
        signup(username);
        return userRepository.findByUsername(username).orElseThrow();
    }

    private void signup(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                username,
                username + "@example.com",
                "password123",
                1998,
                null,
                "ko",
                "KR"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());
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

    private void reportPost(String accessToken, Long mapImageId, String reason) throws Exception {
        mockMvc.perform(post("/map/posts/{id}/report", mapImageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "%s"
                                }
                                """.formatted(reason)))
                .andExpect(status().isCreated());
    }

    private MapImage createMapImage(Long userId) {
        return createMapImage(userId, "테스트 제목");
    }

    private MapImage createMapImage(Long userId, String title) {
        return mapImageRepository.save(
                MapImage.builder()
                        .imageUrl("https://example.com/" + title + ".jpg")
                        .s3Key("test-image-key-" + title)
                        .title(title)
                        .description(title + " 설명")
                        .userId(userId)
                        .username("writer-" + userId)
                        .build()
        );
    }
}
