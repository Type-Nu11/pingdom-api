package com.typenull.pingdom.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.community.api.dto.CommunityPostCommentCreateRequest;
import com.typenull.pingdom.community.api.dto.CommunityPostCreateRequest;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPlaceDailyViewRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostLikeRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostPlaceRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CommunityApiFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private MapPlaceRepository mapPlaceRepository;
    @Autowired private MapImageRepository mapImageRepository;
    @Autowired private CommunityPostRepository postRepository;
    @Autowired private CommunityPostCommentRepository commentRepository;
    @Autowired private CommunityPostLikeRepository likeRepository;
    @Autowired private CommunityPostPlaceRepository postPlaceRepository;
    @Autowired private CommunityPlaceDailyViewRepository dailyViewRepository;

    /**
     * 일일 조회·댓글·좋아요·장소 연결을 먼저 지우고 게시글·장소·사용자를 정리해 통합 흐름의 데이터를 제거.
     */
    @AfterEach
    void cleanup() {
        dailyViewRepository.deleteAllInBatch();
        commentRepository.deleteAllInBatch();
        likeRepository.deleteAllInBatch();
        postPlaceRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        mapImageRepository.deleteAllInBatch();
        mapPlaceRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * 인증 사용자가 카테고리를 조회하고 장소를 연결한 게시글을 만든 뒤 목록·상세·댓글 생성·댓글 조회로 이어지는 HTTP 흐름을 검증.
     */
    @Test
    void connectsCategoryPostAndCommentFlow() throws Exception {
        User author = userRepository.saveAndFlush(user("community-flow-author"));
        MapPlace place = mapPlaceRepository.saveAndFlush(MapPlace.builder()
                .name("통합 테스트 장소").address("서울시 중구").latitude(37.5).longitude(127.0)
                .registrant(author.getUsername()).build());
        String bearer = bearerToken(author);

        mockMvc.perform(get("/community/categories").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].categoryId").value("PLACE"));

        String postResponse = mockMvc.perform(post("/community/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommunityPostCreateRequest(
                                "PLACE", "장소가 재밌네요", "다녀온 후기입니다.", java.util.List.of(place.getId())))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.placeIds[0]").value(place.getId()))
                .andReturn().getResponse().getContentAsString();
        long postId = objectMapper.readTree(postResponse).path("postId").asLong();

        mockMvc.perform(get("/community/categories/PLACE/posts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.posts[0].postId").value(postId));
        mockMvc.perform(get("/community/posts/{postId}", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.places[0].placeId").value(place.getId()));

        mockMvc.perform(post("/community/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommunityPostCommentCreateRequest("좋은 장소네요"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/community/posts/{postId}/comments", postId).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments[0].content").value("좋은 장소네요"));

    }

    /**
     * 미인증 게시글 생성은 401, PLACE 카테고리의 연결 장소 누락은 400과 PLACE_REQUIRED, 없는 글 조회는 404와 POST_NOT_FOUND를 반환하는지 검증.
     */
    @Test
    void returnsCommunityRequestErrors() throws Exception {
        mockMvc.perform(post("/community/posts").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommunityPostCreateRequest("PLACE", "제목", "본문", java.util.List.of()))))
                .andExpect(status().isUnauthorized());

        User author = userRepository.saveAndFlush(user("community-error-author"));
        mockMvc.perform(post("/community/posts").header(HttpHeaders.AUTHORIZATION, bearerToken(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommunityPostCreateRequest("PLACE", "제목", "본문", java.util.List.of()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PLACE_REQUIRED"));
        mockMvc.perform(get("/community/posts/{postId}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(author)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    /**
     * 커뮤니티 API 인증과 게시글 작성을 위한 이메일 인증 완료 일반 사용자를 생성.
     */
    private User user(String username) {
        return User.builder().username(username).email(username + "@example.com").emailVerified(true)
                .password("password").birthYear(1995).language("ko").country("KR").role(UserRole.USER).build();
    }

    /**
     * 저장한 작성자 식별자와 역할을 JWT에 담아 통합 요청의 Bearer 헤더를 생성.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
