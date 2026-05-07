package com.typenull.pingdom.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.PictureReport;
import com.typenull.pingdom.domain.map.domain.PictureReportStatus;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import com.typenull.pingdom.domain.map.repository.PictureReportRepository;
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
        "spring.cloud.aws.credentials.secret-key=test-secret-key"
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
    private PictureReportRepository pictureReportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        pictureReportRepository.deleteAll();
        mapImageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listReportsReturnsRegisteredReports() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner01");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-1.jpg");
        PictureReport pictureReport = createPictureReport(11L, "reporter01", mapImage, "부적절한 사진입니다.");

        mockMvc.perform(get("/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "0")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value(pictureReport.getId()))
                .andExpect(jsonPath("$[0].imageId").value(mapImage.getId()))
                .andExpect(jsonPath("$[0].reportedUserId").value(owner.getId()))
                .andExpect(jsonPath("$[0].status").value(PictureReportStatus.PENDING.name()));
    }

    @Test
    void listReportsAppliesPageParameter() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User firstOwner = createUser("ownerPage01");
        User secondOwner = createUser("ownerPage02");
        MapImage firstImage = createMapImage(firstOwner.getId(), "https://example.com/page-image-1.jpg");
        MapImage secondImage = createMapImage(secondOwner.getId(), "https://example.com/page-image-2.jpg");
        PictureReport firstReport = createPictureReport(21L, "reporterPage01", firstImage, "첫 번째 신고");
        PictureReport latestReport = createPictureReport(22L, "reporterPage02", secondImage, "두 번째 신고");

        mockMvc.perform(get("/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "0")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reportId").value(latestReport.getId()));

        mockMvc.perform(get("/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reportId").value(firstReport.getId()));
    }

    @Test
    void getReportReturnsReportDetail() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner02");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-2.jpg");
        PictureReport pictureReport = createPictureReport(12L, "reporter02", mapImage, "선정적인 이미지입니다.");

        mockMvc.perform(get("/admin/reports/{id}", pictureReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(pictureReport.getId()))
                .andExpect(jsonPath("$.imageId").value(mapImage.getId()))
                .andExpect(jsonPath("$.imageUrl").value(mapImage.getImageUrl()))
                .andExpect(jsonPath("$.status").value(PictureReportStatus.PENDING.name()));
    }

    @Test
    void acceptReportMarksAcceptedAndBansReportedUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner03");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-3.jpg");
        PictureReport pictureReport = createPictureReport(13L, "reporter03", mapImage, "욕설이 포함된 이미지입니다.");

        mockMvc.perform(post("/admin/reports/{id}/accept", pictureReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(pictureReport.getId()))
                .andExpect(jsonPath("$.status").value(PictureReportStatus.ACCEPTED.name()))
                .andExpect(jsonPath("$.reportedUserId").value(owner.getId()))
                .andExpect(jsonPath("$.banned").value(true));

        PictureReport persistedReport = pictureReportRepository.findById(pictureReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();

        assertEquals(PictureReportStatus.ACCEPTED, persistedReport.getStatus());
        assertTrue(persistedOwner.isBanned());
        assertEquals("욕설이 포함된 이미지입니다.", persistedOwner.getBanReason());
    }

    @Test
    void declineReportMarksDeclinedWithoutBanningUser() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner04");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-4.jpg");
        PictureReport pictureReport = createPictureReport(14L, "reporter04", mapImage, "잘못된 위치 정보입니다.");

        mockMvc.perform(post("/admin/reports/{id}/decline", pictureReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(PictureReportStatus.DECLINED.name()))
                .andExpect(jsonPath("$.banned").value(false));

        PictureReport persistedReport = pictureReportRepository.findById(pictureReport.getId()).orElseThrow();
        User persistedOwner = userRepository.findById(owner.getId()).orElseThrow();

        assertEquals(PictureReportStatus.DECLINED, persistedReport.getStatus());
        assertTrue(!persistedOwner.isBanned());
    }

    @Test
    void acceptReportFailsWhenReportAlreadyProcessed() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("owner05");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-5.jpg");
        PictureReport pictureReport = createPictureReport(15L, "reporter05", mapImage, "중복 이미지입니다.");
        pictureReport.decline(java.time.LocalDateTime.now());
        pictureReportRepository.save(pictureReport);

        mockMvc.perform(post("/admin/reports/{id}/accept", pictureReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_PROCESSED"));
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .name("tester")
                .email(username + "@example.com")
                .password(passwordEncoder.encode("password123"))
                .role(UserRole.USER)
                .build());
    }

    private String createAdminAndLogin() throws Exception {
        userRepository.save(User.builder()
                .username("adminTester")
                .name("admin")
                .email("admin@example.com")
                .password(passwordEncoder.encode("password123"))
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
                .userId(userId)
                .build());
    }

    private PictureReport createPictureReport(Long reporterUserId, String reporterUsername, MapImage mapImage, String reason) {
        return pictureReportRepository.save(PictureReport.builder()
                .reporterUserId(reporterUserId)
                .reporterUsername(reporterUsername)
                .mapImage(mapImage)
                .reason(reason)
                .build());
    }
}
