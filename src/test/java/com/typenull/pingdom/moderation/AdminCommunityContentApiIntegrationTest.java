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

    @Test
    void 관리자는_숨김_상태와_카테고리로_글_목록을_조회한다() throws Exception {
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

    @Test
    void 관리자는_숨김_글과_댓글의_상세_및_목록을_조회한다() throws Exception {
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

    @Test
    void 관리자_권한과_없는_대상을_검증한다() throws Exception {
        mockMvc.perform(get("/admin/community/posts").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/community/posts/999999").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get("/admin/community/posts/{postId}/comments/999999", visiblePostId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

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

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
