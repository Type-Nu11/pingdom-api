package com.typenull.pingdom.integration.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.engagement.domain.MapImageLike;
import com.typenull.pingdom.engagement.infrastructure.persistence.MapImageLikeRepository;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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

@Tag("integration")
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
    private MapBookmarkRepository mapBookmarkRepository;

    @Autowired
    private MapImageLikeRepository mapImageLikeRepository;

    @Autowired
    private MapImageRepository mapImageRepository;

    @BeforeEach
    void setUp() {
        mapImageLikeRepository.deleteAllInBatch();
        mapBookmarkRepository.deleteAllInBatch();
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
        Long userId = userRepository.findByUsername("reader01").orElseThrow().getId();
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(secondPlace.getId())
                .build());

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
                .andExpect(jsonPath("$.posts[0].bookmarked").value(true))
                .andExpect(jsonPath("$.posts[1].bookmarked").value(false))
                .andExpect(jsonPath("$.posts[0].placeName").value("두 번째 장소"));
    }

    @Test
    void listBookmarkedPostsReturnsOnlyCurrentUsersSavedPosts() throws Exception {
        String accessToken = signupAndLogin("bookmark-reader");
        Long userId = userRepository.findByUsername("bookmark-reader").orElseThrow().getId();
        MapPlace savedPlace = createMapPlace("저장한 장소", "경상남도 진주시 저장로 1");
        MapPlace unsavedPlace = createMapPlace("저장하지 않은 장소", "경상남도 진주시 미저장로 2");
        createMapImage(31L, "writer01", "이전 저장 게시글", savedPlace, 4L);
        MapImage savedPost = createMapImage(33L, "writer03", "최신 저장 게시글", savedPlace, 6L);
        createMapImage(32L, "writer02", "저장하지 않은 게시글", unsavedPlace, 2L);
        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(savedPlace.getId())
                .build());

        mockMvc.perform(get("/map/bookmarks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(savedPost.getId()))
                .andExpect(jsonPath("$.posts[0].title").value("최신 저장 게시글"))
                .andExpect(jsonPath("$.posts[0].bookmarked").value(true))
                .andExpect(jsonPath("$.posts[0].placeId").value(savedPlace.getId()));
    }

    @Test
    void listLikedPostsReturnsOnlyCurrentUsersLikedPostsInLatestLikeOrder() throws Exception {
        String accessToken = signupAndLogin("like-reader");
        Long userId = userRepository.findByUsername("like-reader").orElseThrow().getId();

        MapPlace likedPlace = createMapPlace("좋아요한 장소", "경상남도 진주시 좋아요로 1");
        MapPlace anotherPlace = createMapPlace("다른 장소", "경상남도 진주시 좋아요로 2");

        MapImage olderLikedPost = createMapImage(41L, "writer01", "먼저 좋아요한 게시글", likedPlace, 4L);
        MapImage latestLikedPost = createMapImage(42L, "writer02", "나중에 좋아요한 게시글", anotherPlace, 9L);
        createMapImage(43L, "writer03", "좋아요하지 않은 게시글", anotherPlace, 1L);

        mapBookmarkRepository.save(MapBookmark.builder()
                .userId(userId)
                .placeId(anotherPlace.getId())
                .build());

        mapImageLikeRepository.save(MapImageLike.builder()
                .userId(userId)
                .mapImageId(olderLikedPost.getId())
                .build());
        mapImageLikeRepository.save(MapImageLike.builder()
                .userId(userId)
                .mapImageId(latestLikedPost.getId())
                .build());

        mockMvc.perform(get("/map/likes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(latestLikedPost.getId()))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(true))
                .andExpect(jsonPath("$.posts[0].bookmarked").value(true))
                .andExpect(jsonPath("$.posts[0].placeName").value("다른 장소"))
                .andExpect(jsonPath("$.posts[1].id").value(olderLikedPost.getId()))
                .andExpect(jsonPath("$.posts[1].likedByMe").value(true))
                .andExpect(jsonPath("$.posts[1].bookmarked").value(false));
    }

    @Test
    void removedLegacyLikedPostsReadPathIsNotMapped() throws Exception {
        String accessToken = signupAndLogin("like-alias-reader");
        Long userId = userRepository.findByUsername("like-alias-reader").orElseThrow().getId();

        MapPlace mapPlace = createMapPlace("좋아요 alias 장소", "경상남도 진주시 alias로 1");
        MapImage likedPost = createMapImage(61L, "writer-alias", "좋아요 alias 게시글", mapPlace, 3L);
        mapImageLikeRepository.save(MapImageLike.builder()
                .userId(userId)
                .mapImageId(likedPost.getId())
                .build());

        mockMvc.perform(get("/map/likes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(likedPost.getId()));

        mockMvc.perform(get("/map/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listPostsExcludesHiddenPosts() throws Exception {
        String accessToken = signupAndLogin("hidden-reader");
        MapPlace mapPlace = createMapPlace("숨김 장소", "경상남도 진주시 숨김로 1");
        MapImage visiblePost = createMapImage(51L, "visible-writer", "노출 게시글", mapPlace, 1L);
        MapImage hiddenPost = createMapImage(52L, "hidden-writer", "숨김 게시글", mapPlace, 1L);
        hiddenPost.autoHide("테스트 숨김", java.time.LocalDateTime.now(), null);
        mapImageRepository.saveAndFlush(hiddenPost);

        mockMvc.perform(get("/map/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .param("page", "1")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(visiblePost.getId()));

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
