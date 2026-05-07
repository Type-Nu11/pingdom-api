package com.typenull.pingdom.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.domain.UserRole;
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
import org.springframework.test.web.servlet.MockMvc;
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
    private UserRepository userRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @Autowired
    private PictureReportRepository pictureReportRepository;

    @BeforeEach
    void setUp() {
        pictureReportRepository.deleteAll();
        mapImageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listReportsReturnsRegisteredReports() throws Exception {
        User owner = createUser("owner01");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-1.jpg");
        createPictureReport(11L, "reporter01", mapImage, "부적절한 사진입니다.");

        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value(pictureReportRepository.findAll().get(0).getId()))
                .andExpect(jsonPath("$[0].imageId").value(mapImage.getId()))
                .andExpect(jsonPath("$[0].reportedUserId").value(owner.getId()))
                .andExpect(jsonPath("$[0].status").value(PictureReportStatus.PENDING.name()));
    }

    @Test
    void getReportReturnsReportDetail() throws Exception {
        User owner = createUser("owner02");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-2.jpg");
        PictureReport pictureReport = createPictureReport(12L, "reporter02", mapImage, "선정적인 이미지입니다.");

        mockMvc.perform(get("/admin/reports/{id}", pictureReport.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(pictureReport.getId()))
                .andExpect(jsonPath("$.imageId").value(mapImage.getId()))
                .andExpect(jsonPath("$.imageUrl").value(mapImage.getImageUrl()))
                .andExpect(jsonPath("$.status").value(PictureReportStatus.PENDING.name()));
    }

    @Test
    void acceptReportMarksAcceptedAndBansReportedUser() throws Exception {
        User owner = createUser("owner03");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-3.jpg");
        PictureReport pictureReport = createPictureReport(13L, "reporter03", mapImage, "욕설이 포함된 이미지입니다.");

        mockMvc.perform(post("/admin/reports/{id}/accept", pictureReport.getId()))
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
        User owner = createUser("owner04");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-4.jpg");
        PictureReport pictureReport = createPictureReport(14L, "reporter04", mapImage, "잘못된 위치 정보입니다.");

        mockMvc.perform(post("/admin/reports/{id}/decline", pictureReport.getId()))
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
        User owner = createUser("owner05");
        MapImage mapImage = createMapImage(owner.getId(), "https://example.com/image-5.jpg");
        PictureReport pictureReport = createPictureReport(15L, "reporter05", mapImage, "중복 이미지입니다.");
        pictureReport.decline(java.time.LocalDateTime.now());
        pictureReportRepository.save(pictureReport);

        mockMvc.perform(post("/admin/reports/{id}/accept", pictureReport.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_PROCESSED"));
    }

    private User createUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .name("tester")
                .email(username + "@example.com")
                .password("password123")
                .role(UserRole.USER)
                .build());
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
