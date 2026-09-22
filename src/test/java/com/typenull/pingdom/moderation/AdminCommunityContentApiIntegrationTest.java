package com.typenull.pingdom.moderation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCommunityContentApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityPostRepository postRepository;
    @Autowired private CommunityPostCommentRepository commentRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private long visiblePostId;
    private long hiddenPostId;
    private long visibleCommentId;
    private long hiddenCommentId;
    private String adminBearer;
    private String userBearer;

    /**
     * 일반 사용자·관리자와 공개/숨김 글·댓글을 저장하고 역할별 인증 토큰을 준비한다.
     */
    @BeforeEach
    void setup() {
        User author = userRepository.saveAndFlush(user("community-content-user", UserRole.USER));
        User admin = userRepository.saveAndFlush(user("community-content-admin", UserRole.ADMIN));
        CommunityPost visiblePost = postRepository.saveAndFlush(CommunityPost.create("TRAVEL", "공개 글", "공개 본문", author.getId()));
        CommunityPost hiddenPost = CommunityPost.create("PLACE", "숨김 글", "숨김 본문", author.getId());
        hiddenPost.hide(admin.getId(), LocalDateTime.now(ZoneOffset.UTC));
        hiddenPost = postRepository.saveAndFlush(hiddenPost);
        CommunityPostComment visibleComment = commentRepository.saveAndFlush(
                CommunityPostComment.create(visiblePost, author.getId(), "공개 댓글")
        );
        CommunityPostComment hiddenComment = CommunityPostComment.create(visiblePost, author.getId(), "숨김 댓글");
        hiddenComment.hide(admin.getId(), LocalDateTime.now(ZoneOffset.UTC));
        hiddenComment = commentRepository.saveAndFlush(hiddenComment);
        visiblePostId = visiblePost.getId();
        hiddenPostId = hiddenPost.getId();
        visibleCommentId = visibleComment.getId();
        hiddenCommentId = hiddenComment.getId();
        adminBearer = bearer(admin);
        userBearer = bearer(author);
    }

    /**
     * 관리자 글 목록의 hidden 필터는 숨김 글만, 카테고리 필터는 해당 공개 글만 반환하며 전체 수를 맞추는지 검증한다.
     */
    @Test
    void filtersAdminPostsByVisibilityAndCategory() throws Exception {
        mockMvc.perform(get("/admin/community/posts").param("hidden", "true")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.posts[0].postId").value(hiddenPostId))
                .andExpect(jsonPath("$.posts[0].hidden").value(true));
        mockMvc.perform(get("/admin/community/posts").param("categoryId", "TRAVEL")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.posts[0].postId").value(visiblePostId));
    }

    /**
     * 관리자는 숨김 글 상세와 숨김 댓글 목록을 조회할 수 있고 공개 댓글 상세도 공개 상태로 반환하는지 검증한다.
     */
    @Test
    void readsAdminContentAcrossVisibilityStates() throws Exception {
        mockMvc.perform(get("/admin/community/posts/{postId}", hiddenPostId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("숨김 글"))
                .andExpect(jsonPath("$.hidden").value(true));
        mockMvc.perform(get("/admin/community/posts/{postId}/comments", visiblePostId).param("hidden", "true")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.comments[0].commentId").value(hiddenCommentId));
        mockMvc.perform(get("/admin/community/posts/{postId}/comments/{commentId}", visiblePostId, visibleCommentId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("공개 댓글"))
                .andExpect(jsonPath("$.hidden").value(false));
    }

    /**
     * 일반 사용자의 관리자 목록 접근은 403이며 없는 글·댓글 조회는 각 대상 없음 코드와 404를 반환하는지 검증한다.
     */
    @Test
    void rejectsUnauthorizedOrMissingContent() throws Exception {
        mockMvc.perform(get("/admin/community/posts").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/community/posts/999999").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get("/admin/community/posts/{postId}/comments/999999", visiblePostId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    /**
     * 일반 조회와 관리자 조회의 권한 차이를 검사할 이메일 인증 완료 사용자를 지정 역할로 만든다.
     */
    private User user(String username, UserRole role) {
        return User.builder()
                .username(username)
                .email(username + "@example.com")
                .emailVerified(true)
                .password("password")
                .birthYear(1995)
                .language("ko")
                .country("KR")
                .role(role)
                .build();
    }

    /**
     * 저장된 사용자 ID와 역할로 관리자 또는 일반 요청의 Bearer 토큰을 발급한다.
     */
    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
