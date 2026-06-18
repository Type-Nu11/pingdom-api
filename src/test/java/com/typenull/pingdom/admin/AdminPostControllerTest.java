package com.typenull.pingdom.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.domain.PostReport;
import com.typenull.pingdom.engagement.domain.PostReportStatus;
import com.typenull.pingdom.engagement.infrastructure.persistence.PostReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
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
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@Transactional
class AdminPostControllerTest {

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
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        postReportRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listPostsIncludesReportsInSamePostItem() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("postOwner01");
        MapImage mapImage = createMapImage(owner.getId(), owner.getUsername(), "https://example.com/image-1.jpg");

        User reporter1 = createUser("reporter01");
        User reporter2 = createUser("reporter02");

        PostReport olderReport = createPostReport(reporter1.getId(), reporter1.getUsername(), mapImage, "첫 번째 신고");
        PostReport newerReport = createPostReport(reporter2.getId(), reporter2.getUsername(), mapImage, "두 번째 신고");

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].id").value(mapImage.getId()))
                .andExpect(jsonPath("$.posts[0].reports.length()").value(2))
                .andExpect(jsonPath("$.posts[0].reports[0].reportId").value(newerReport.getId()))
                .andExpect(jsonPath("$.posts[0].reports[0].reporterUserId").value(reporter2.getId()))
                .andExpect(jsonPath("$.posts[0].reports[0].reporterUsername").value("reporter02"))
                .andExpect(jsonPath("$.posts[0].reports[0].reason").value("두 번째 신고"))
                .andExpect(jsonPath("$.posts[0].reports[0].status").value(PostReportStatus.PENDING.name()))
                .andExpect(jsonPath("$.posts[0].reports[0].processedAt").doesNotExist())
                .andExpect(jsonPath("$.posts[0].reports[1].reportId").value(olderReport.getId()));
    }

    @Test
    void listPostsReturnsEmptyReportsWhenNoReportExists() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("postOwner02");
        MapImage mapImage = createMapImage(owner.getId(), owner.getUsername(), "https://example.com/image-2.jpg");

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].id").value(mapImage.getId()))
                .andExpect(jsonPath("$.posts[0].reports.length()").value(0));
    }

    @Test
    void getPostReturnsDetailWithReports() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("postOwner03");
        MapImage mapImage = createMapImage(owner.getId(), owner.getUsername(), "https://example.com/image-3.jpg");

        User reporter = createUser("reporter03");
        PostReport report = createPostReport(reporter.getId(), reporter.getUsername(), mapImage, "상세 신고");

        mockMvc.perform(get("/admin/posts/{id}", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapImage.getId()))
                .andExpect(jsonPath("$.name").value("신고 대상 제목"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/image-3.jpg"))
                .andExpect(jsonPath("$.userId").value(owner.getId()))
                .andExpect(jsonPath("$.username").value(owner.getUsername()))
                .andExpect(jsonPath("$.reports.length()").value(1))
                .andExpect(jsonPath("$.reports[0].reportId").value(report.getId()))
                .andExpect(jsonPath("$.reports[0].reporterUserId").value(reporter.getId()))
                .andExpect(jsonPath("$.reports[0].reporterUsername").value("reporter03"))
                .andExpect(jsonPath("$.reports[0].reason").value("상세 신고"))
                .andExpect(jsonPath("$.reports[0].status").value(PostReportStatus.PENDING.name()));
    }

    @Test
    void getPostReturnsNotFoundWhenPostDoesNotExist() throws Exception {
        String adminAccessToken = createAdminAndLogin();

        mockMvc.perform(get("/admin/posts/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void listPostsFiltersByKeywordAcrossPostAndPlaceFields() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("keywordOwner");

        MapPlace matchingPlace = mapPlaceRepository.save(MapPlace.builder()
                .name("대구소프트웨어마이스터고등학교")
                .address("대구광역시 달성군 구지면 창리로11길 93")
                .latitude(35.6427)
                .longitude(128.3916)
                .userId(owner.getId())
                .registrant(owner.getUsername())
                .build());

        MapImage matchingPost = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/matching-image.jpg")
                .s3Key("matching-key")
                .title("검색 대상 게시글")
                .description("장소명 검색 테스트")
                .userId(owner.getId())
                .username(owner.getUsername())
                .mapPlace(matchingPlace)
                .build());

        mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/other-image.jpg")
                .s3Key("other-key")
                .title("다른 게시글")
                .description("검색되지 않아야 함")
                .userId(owner.getId())
                .username(owner.getUsername())
                .build());

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", "대구소프트웨어마이스터고등학교"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(matchingPost.getId()))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void listPostsMatchesPostIdExactlyWhenKeywordIsNumeric() throws Exception {
        String adminAccessToken = createAdminAndLogin();
        User owner = createUser("numericKeywordOwner");

        MapImage firstPost = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/post-1.jpg")
                .s3Key("post-1")
                .title("첫 번째 게시글")
                .description("숫자 검색 테스트")
                .userId(owner.getId())
                .username(owner.getUsername())
                .build());

        MapImage secondPost = mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/post-10.jpg")
                .s3Key("post-10")
                .title("두 번째 게시글")
                .description("숫자 검색 테스트")
                .userId(owner.getId())
                .username(owner.getUsername())
                .build());

        mockMvc.perform(get("/admin/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                        .param("keyword", String.valueOf(firstPost.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(firstPost.getId()))
                .andExpect(jsonPath("$.posts[0].id").value(org.hamcrest.Matchers.not(secondPost.getId().intValue())));
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

    private MapImage createMapImage(Long userId, String username, String imageUrl) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl(imageUrl)
                .s3Key("test-key-" + userId)
                .title("신고 대상 제목")
                .description("신고 대상 설명")
                .userId(userId)
                .username(username)
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
