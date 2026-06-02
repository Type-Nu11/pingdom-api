package com.typenull.pingdom.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.PostReport;
import com.typenull.pingdom.domain.map.domain.PostReportStatus;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.PostReportRepository;
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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        postReportRepository.deleteAll();
        mapImageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void acceptReportMarksAcceptedAndBansReportedUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner03");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-3.jpg");
        PostReport postReport = createPostReport(13L, "reporter03", mapImage, "욕설이 포함된 이미지입니다.");

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
        assertTrue(mapImageRepository.findById(mapImage.getId()).isEmpty());
    }

    @Test
    void declineReportMarksDeclinedWithoutBanningUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner04");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-4.jpg");
        PostReport postReport = createPostReport(14L, "reporter04", mapImage, "잘못된 위치 정보입니다.");

        mockMvc.perform(post("/admin/reports/{id}/decline", postReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(PostReportStatus.DECLINED.name()))
                .andExpect(jsonPath("$.banned").value(false));

        PostReport persistedReport = postReportRepository.findById(postReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();

        assertEquals(PostReportStatus.DECLINED, persistedReport.getStatus());
        assertTrue(!persistedOwner.isBanned());
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
