package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
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

    @BeforeEach
    void setUp() {
        postReportRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void reportRegistersPostReport() throws Exception {
        String accessToken = signupAndLogin("reporter01");
        MapImage mapImage = createMapImage(101L);

        mockMvc.perform(post("/map/post/{id}/report", mapImage.getId())
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
    }

    @Test
    void reportSucceedsWithSameTokenThatCanAccessMyPage() throws Exception {
        String accessToken = signupAndLogin("reporter05");
        MapImage mapImage = createMapImage(105L);

        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/map/post/{id}/report", mapImage.getId())
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

        mockMvc.perform(post("/map/post/{id}/report", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "중복 신고 테스트"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/map/post/{id}/report", mapImage.getId())
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

        mockMvc.perform(post("/map/post/{id}/report", 9999L)
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

        mockMvc.perform(post("/map/post/{id}/report", mapImage.getId())
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
                        .title("테스트 제목")
                        .description("테스트 설명")
                        .userId(userId)
                        .build()
        );
    }
}
