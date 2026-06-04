package com.typenull.pingdom.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
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
class MapPostQueryControllerTest {

    @MockBean
    private S3Client s3Client;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MapPlaceRepository mapPlaceRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @BeforeEach
    void setUp() {
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void listPostsReturnsLatestPostsWithPagingMetadata() throws Exception {
        String accessToken = signupAndLogin("reader01");
        MapPlace firstPlace = createMapPlace("첫 번째 장소", "경상남도 진주시 진양호로 1");
        MapPlace secondPlace = createMapPlace("두 번째 장소", "경상남도 진주시 남강로 2");
        createMapImage(11L, "writer01", "첫 번째 게시글", firstPlace, 3L);
        MapImage latestPost = createMapImage(12L, "writer02", "두 번째 게시글", secondPlace, 7L);

        mockMvc.perform(get("/map/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(latestPost.getId()))
                .andExpect(jsonPath("$.posts[0].title").value("두 번째 게시글"))
                .andExpect(jsonPath("$.posts[0].username").value("writer02"))
                .andExpect(jsonPath("$.posts[0].likeCount").value(7))
                .andExpect(jsonPath("$.posts[0].placeName").value("두 번째 장소"));
    }

    @Test
    void getPostReturnsPostDetailWithPlaceInformation() throws Exception {
        String accessToken = signupAndLogin("reader02");
        MapPlace mapPlace = createMapPlace("진주성", "경상남도 진주시 남강로 626");
        MapImage mapImage = createMapImage(21L, "writer-detail", "상세 게시글", mapPlace, 12L);

        mockMvc.perform(get("/map/posts/{id}", mapImage.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mapImage.getId()))
                .andExpect(jsonPath("$.title").value("상세 게시글"))
                .andExpect(jsonPath("$.username").value("writer-detail"))
                .andExpect(jsonPath("$.likeCount").value(12))
                .andExpect(jsonPath("$.placeId").value(mapPlace.getId()))
                .andExpect(jsonPath("$.placeName").value("진주성"))
                .andExpect(jsonPath("$.placeAddress").value("경상남도 진주시 남강로 626"))
                .andExpect(jsonPath("$.latitude").value(35.1801))
                .andExpect(jsonPath("$.longitude").value(128.1078));
    }

    @Test
    void getPostFailsWhenPostDoesNotExist() throws Exception {
        String accessToken = signupAndLogin("reader03");

        mockMvc.perform(get("/map/posts/{id}", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMAGE_NOT_FOUND"));
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

    private MapPlace createMapPlace(String name, String address) {
        return mapPlaceRepository.save(MapPlace.builder()
                .name(name)
                .address(address)
                .latitude(35.1801)
                .longitude(128.1078)
                .userId(1L)
                .registrant("placeOwner")
                .build());
    }

    private MapImage createMapImage(Long userId, String username, String title, MapPlace mapPlace, Long likeCount) {
        return mapImageRepository.save(MapImage.builder()
                .imageUrl("https://example.com/" + title + ".jpg")
                .s3Key("test-key-" + title)
                .title(title)
                .description(title + " 설명")
                .userId(userId)
                .username(username)
                .likeCount(likeCount)
                .mapPlace(mapPlace)
                .build());
    }
}
