package com.typenull.pingdom.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityReport;
import com.typenull.pingdom.community.domain.CommunityReportReason;
import com.typenull.pingdom.community.domain.CommunityReportStatus;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
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
class AdminCommunityReportApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityPostRepository postRepository;
    @Autowired private CommunityPostCommentRepository commentRepository;
    @Autowired private CommunityReportRepository reportRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private long postId;
    private long commentId;
    private long postReportId;
    private long commentReportId;
    private String adminBearer;
    private String userBearer;

    @BeforeEach
    void setup() {
        User reporter = userRepository.saveAndFlush(user("community-report-user", UserRole.USER));
        User admin = userRepository.saveAndFlush(user("community-report-admin", UserRole.ADMIN));
        CommunityPost post = postRepository.saveAndFlush(CommunityPost.create("TRAVEL", "제목", "본문", reporter.getId()));
        CommunityPostComment comment = commentRepository.saveAndFlush(
                CommunityPostComment.create(post, reporter.getId(), "댓글")
        );
        postId = post.getId();
        commentId = comment.getId();
        postReportId = reportRepository.saveAndFlush(CommunityReport.reportPost(
                reporter.getId(), post, CommunityReportReason.SPAM, "글 신고", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        )).getId();
        commentReportId = reportRepository.saveAndFlush(CommunityReport.reportComment(
                reporter.getId(), comment, CommunityReportReason.ABUSE, "댓글 신고", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        )).getId();
        adminBearer = bearer(admin);
        userBearer = bearer(reporter);
    }

    @Test
    void 관리자는_상태와_대상_유형으로_신고를_조회하고_상세를_확인한다() throws Exception {
        mockMvc.perform(get("/admin/community-reports")
                        .param("status", "PENDING").param("targetType", "POST")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.reports[0].reportId").value(postReportId))
                .andExpect(jsonPath("$.reports[0].targetType").value("POST"))
                .andExpect(jsonPath("$.reports[0].targetHidden").value(false));
        mockMvc.perform(get("/admin/community-reports/{reportId}", commentReportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(commentReportId))
                .andExpect(jsonPath("$.targetType").value("COMMENT"))
                .andExpect(jsonPath("$.description").value("댓글 신고"));
    }

    @Test
    void 글_신고를_수락하면_글을_숨기고_일반_접근을_차단한다() throws Exception {
        mockMvc.perform(post("/admin/community-reports/{reportId}/accept", postReportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.targetHidden").value(true));

        assertThat(postRepository.findById(postId).orElseThrow().isHidden()).isTrue();
        assertThat(reportRepository.findById(postReportId).orElseThrow().getStatus())
                .isEqualTo(CommunityReportStatus.ACCEPTED);
        mockMvc.perform(get("/community/posts/{postId}", postId).header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/community/categories/TRAVEL/posts").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        mockMvc.perform(post("/community/posts/{postId}/comments", postId)
                .header(HttpHeaders.AUTHORIZATION, userBearer).contentType("application/json")
                .content("{\"content\":\"새 댓글\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/community/posts/{postId}/likes", postId)
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/community/posts/{postId}/reports", postId)
                        .header(HttpHeaders.AUTHORIZATION, userBearer).contentType("application/json")
                        .content("{\"reason\":\"SPAM\",\"description\":\"다시 신고\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 댓글_신고를_수락하면_댓글만_숨긴다() throws Exception {
        mockMvc.perform(post("/admin/community-reports/{reportId}/accept", commentReportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.targetType").value("COMMENT"))
                .andExpect(jsonPath("$.targetHidden").value(true));

        assertThat(postRepository.findById(postId).orElseThrow().isHidden()).isFalse();
        assertThat(commentRepository.findById(commentId).orElseThrow().isHidden()).isTrue();
        mockMvc.perform(get("/community/posts/{postId}", postId).header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/community/posts/{postId}/comments", postId).header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void 반려는_대상을_숨기지_않고_처리된_신고는_다시_처리할_수_없다() throws Exception {
        mockMvc.perform(post("/admin/community-reports/{reportId}/decline", postReportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.targetHidden").value(false));
        assertThat(postRepository.findById(postId).orElseThrow().isHidden()).isFalse();
        mockMvc.perform(post("/admin/community-reports/{reportId}/accept", postReportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_ALREADY_PROCESSED"));
    }

    @Test
    void 관리자_권한과_없는_신고를_검증한다() throws Exception {
        mockMvc.perform(get("/admin/community-reports").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/community-reports")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/admin/community-reports/999999/accept").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
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
