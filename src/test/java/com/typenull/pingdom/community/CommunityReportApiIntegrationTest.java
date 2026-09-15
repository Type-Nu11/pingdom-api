package com.typenull.pingdom.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.community.domain.CommunityPost;
import com.typenull.pingdom.community.domain.CommunityPostComment;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityPostCommentRepository;
import com.typenull.pingdom.community.infrastructure.persistence.CommunityReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunityReportApiIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private CommunityPostRepository posts;
    @Autowired private CommunityPostCommentRepository comments;
    @Autowired private CommunityReportRepository reports;
    @Autowired private UserRepository users;
    @Autowired private JwtTokenProvider tokens;

    private long postId;
    private long commentId;
    private long reporterId;
    private String bearer;
    private static final String BODY = "{\"reason\":\"SPAM\",\"description\":\" 도배입니다 \"}";

    @BeforeEach
    void setup() {
        User user = users.saveAndFlush(User.builder().username("report-api-user")
                .email("report-api@example.com").emailVerified(true).password("password")
                .birthYear(1995).language("ko").country("KR").role(UserRole.USER).build());
        reporterId = user.getId();
        bearer = "Bearer " + tokens.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        CommunityPost target = posts.saveAndFlush(CommunityPost.create("TRAVEL", "제목", "내용", reporterId));
        postId = target.getId();
        commentId = comments.saveAndFlush(CommunityPostComment.create(target, reporterId, "댓글")).getId();
    }

    @Test
    void 글과_댓글_신고를_인증된_사용자로_저장한다() throws Exception {
        mvc.perform(post(postUrl()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post(commentUrl()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(reports.existsByReporterUserIdAndPost_Id(reporterId, postId)).isTrue();
        assertThat(reports.existsByReporterUserIdAndComment_Id(reporterId, commentId)).isTrue();
        assertThat(reports.findAll()).allSatisfy(report -> {
            assertThat(report.getReporterUserId()).isEqualTo(reporterId);
            assertThat(report.getDescription()).isEqualTo("도배입니다");
        });
    }

    @Test
    void 글과_댓글_재신고는_처리_후에도_거절한다() throws Exception {
        for (String url : new String[] {postUrl(), commentUrl()}) {
            mvc.perform(post(url).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated());
            mvc.perform(post(url).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        }
        reports.findAll().forEach(report -> report.decline(reporterId, LocalDateTime.now().plusDays(1)));
        reports.flush();
        for (String url : new String[] {postUrl(), commentUrl()}) {
            mvc.perform(post(url).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"SPAM\"}", "{\"reason\":\"UNKNOWN\",\"description\":\"설명\"}",
            "{\"reason\":\"SPAM\",\"description\":\"   \"}"})
    void 잘못된_입력은_저장하지_않는다(String body) throws Exception {
        for (String url : new String[] {postUrl(), commentUrl()}) {
            mvc.perform(post(url).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(reports.count()).isZero();
    }

    @Test
    void 설명_최대_길이를_검증한다() throws Exception {
        String body = "{\"reason\":\"OTHER\",\"description\":\"" + "가".repeat(501) + "\"}";
        mvc.perform(post(postUrl()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(reports.count()).isZero();
    }

    @Test
    void 미인증_요청을_거절한다() throws Exception {
        for (String url : new String[] {postUrl(), commentUrl()}) {
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        }
        assertThat(reports.count()).isZero();
    }

    @Test
    void 없는_글과_댓글_또는_다른_글의_댓글을_거절한다() throws Exception {
        mvc.perform(post("/community/posts/999999/reports").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mvc.perform(post("/community/posts/{postId}/comments/999999/reports", postId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        long otherId = posts.saveAndFlush(CommunityPost.create("TRAVEL", "다른 글", "내용", reporterId)).getId();
        mvc.perform(post("/community/posts/{postId}/comments/{commentId}/reports", otherId, commentId)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        assertThat(reports.count()).isZero();
    }

    private String postUrl() { return "/community/posts/" + postId + "/reports"; }
    private String commentUrl() { return "/community/posts/" + postId + "/comments/" + commentId + "/reports"; }
}
