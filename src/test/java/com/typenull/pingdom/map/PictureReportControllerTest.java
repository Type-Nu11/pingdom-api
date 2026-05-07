package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.domain.auth.dto.login.LoginRequest;
import com.typenull.pingdom.domain.auth.dto.signup.SignupRequest;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.domain.PictureReport;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class PictureReportControllerTest {

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

    @BeforeEach
    void setUp() {
        pictureReportRepository.deleteAll();
        mapImageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void reportRegistersPictureReport() throws Exception {
        String accessToken = signupAndLogin("reporter01");
        MapImage mapImage = createMapImage(101L);

        mockMvc.perform(post("/map/pictures/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "부적절한 사진입니다."
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(1L, pictureReportRepository.count());
        PictureReport pictureReport = pictureReportRepository.findAll().get(0);
        assertEquals("reporter01", pictureReport.getReporterUsername());
        assertEquals("부적절한 사진입니다.", pictureReport.getReason());
        assertEquals(mapImage.getId(), pictureReport.getMapImage().getId());
    }

    @Test
    void reportFailsWhenUserReportsSamePictureTwice() throws Exception {
        String accessToken = signupAndLogin("reporter02");
        MapImage mapImage = createMapImage(102L);

        mockMvc.perform(post("/map/pictures/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "중복 신고 테스트"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/pictures/{id}/report", mapImage.getId())
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
    void reportFailsWhenPictureDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("reporter03");

        mockMvc.perform(post("/map/pictures/{id}/report", 9999L)
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

        mockMvc.perform(post("/map/pictures/{id}/report", mapImage.getId())
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

    private String signupAndLogin(String username) throws Exception {
        SignupRequest signupRequest = new SignupRequest(
                username,
                "tester",
                username + "@example.com",
                "password123"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

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

    private MapImage createMapImage(Long userId) {
        return mapImageRepository.save(
                MapImage.builder()
                        .imageUrl("https://example.com/image.jpg")
                        .s3Key("test-image-key")
                        .userId(userId)
                        .build()
        );
    }
}
