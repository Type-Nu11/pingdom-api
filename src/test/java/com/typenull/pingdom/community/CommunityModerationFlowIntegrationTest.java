package com.typenull.pingdom.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.domain.CommunityReport;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunityModerationFlowIntegrationTest {

    private static final String REPORT_REQUEST = "{\"reason\":\"SPAM\",\"description\":\"도배 신고입니다\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityPostRepository postRepository;
    @Autowired private CommunityPostCommentRepository commentRepository;
    @Autowired private CommunityReportRepository reportRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private long postId;
    private long commentId;
    private String reporterBearer;
    private String adminBearer;

    @BeforeEach
    void setUp() {
        User author = userRepository.saveAndFlush(user("moderation-flow-author", UserRole.USER));
        User reporter = userRepository.saveAndFlush(user("moderation-flow-reporter", UserRole.USER));
        User admin = userRepository.saveAndFlush(user("moderation-flow-admin", UserRole.ADMIN));
        CommunityPost post = postRepository.saveAndFlush(CommunityPost.create("TRAVEL", "신고 대상 글", "본문", author.getId()));
        CommunityPostComment comment = commentRepository.saveAndFlush(
                CommunityPostComment.create(post, author.getId(), "신고 대상 댓글")
        );
        postId = post.getId();
        commentId = comment.getId();
        reporterBearer = bearer(reporter);
        adminBearer = bearer(admin);
    }

    @Test
    void 글_신고부터_관리자_수락과_일반_조회_차단까지_검증한다() throws Exception {
        mockMvc.perform(post(postReportPath()).header(HttpHeaders.AUTHORIZATION, reporterBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        long reportId = reportId();
        mockMvc.perform(get("/admin/community-reports").param("status", "PENDING").param("targetType", "POST")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.reports[0].reportId").value(reportId));
        mockMvc.perform(get("/admin/community-reports/{reportId}", reportId).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("도배 신고입니다"));

        mockMvc.perform(post("/admin/community-reports/{reportId}/accept", reportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.targetHidden").value(true));

        assertThat(postRepository.findById(postId).orElseThrow().isHidden()).isTrue();
        mockMvc.perform(get("/admin/community/posts/{postId}", postId).header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true));
        mockMvc.perform(get("/community/posts/{postId}", postId)
                        .header(HttpHeaders.AUTHORIZATION, reporterBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 댓글_신고를_반려하면_일반_조회는_유지된다() throws Exception {
        mockMvc.perform(post(commentReportPath()).header(HttpHeaders.AUTHORIZATION, reporterBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        long reportId = reportId();
        mockMvc.perform(post("/admin/community-reports/{reportId}/decline", reportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.targetHidden").value(false));

        assertThat(commentRepository.findById(commentId).orElseThrow().isHidden()).isFalse();
        mockMvc.perform(get("/community/posts/{postId}/comments", postId)
                        .header(HttpHeaders.AUTHORIZATION, reporterBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.comments[0].commentId").value(commentId));
        mockMvc.perform(get("/admin/community-reports").param("status", "DECLINED").param("targetType", "COMMENT")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].targetHidden").value(false));
    }

    @Test
    void 인증과_권한_중복_신고_중복_처리_없는_대상_오류를_검증한다() throws Exception {
        mockMvc.perform(post(postReportPath()).contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(postReportPath()).header(HttpHeaders.AUTHORIZATION, reporterBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))
                .andExpect(status().isCreated());
        long reportId = reportId();
        mockMvc.perform(post(postReportPath()).header(HttpHeaders.AUTHORIZATION, reporterBearer)
                        .contentType(MediaType.APPLICATION_JSON).content(REPORT_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        mockMvc.perform(get("/admin/community-reports").header(HttpHeaders.AUTHORIZATION, reporterBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/community-reports/{reportId}/decline", reportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/community-reports/{reportId}/accept", reportId)
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_PROCESSED"));
        mockMvc.perform(get("/admin/community-reports/999999").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    private long reportId() {
        return reportRepository.findAll().stream()
                .max(Comparator.comparing(CommunityReport::getId))
                .orElseThrow()
                .getId();
    }

    private String postReportPath() {
        return "/community/posts/" + postId + "/reports";
    }

    private String commentReportPath() {
        return "/community/posts/" + postId + "/comments/" + commentId + "/reports";
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
