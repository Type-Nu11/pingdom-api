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

    /**
     * 인증 사용자와 신고 대상 글·댓글을 저장해 신고자 식별자 및 두 신고 경로를 준비한다.
     */
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

    /**
     * 글·댓글 신고가 201과 PENDING을 반환하고 인증 사용자 ID와 양끝 공백이 제거된 설명으로 저장되는지 검증한다.
     */
    @Test
    void storesAuthenticatedContentReports() throws Exception {
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

    /**
     * 같은 사용자의 글·댓글 재신고는 대기 중에도, 기존 신고 반려 후에도 409로 거절되는지 검증한다.
     */
    @Test
    void rejectsRepeatedContentReports() throws Exception {
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

    /**
     * 필수값 누락·알 수 없는 사유·공백 설명을 글과 댓글 신고 API에 전달하면 모두 400이며 저장된 신고가 없는지 검증한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"SPAM\"}", "{\"reason\":\"UNKNOWN\",\"description\":\"설명\"}",
            "{\"reason\":\"SPAM\",\"description\":\"   \"}"})
    void rejectsInvalidReportInput(String body) throws Exception {
        for (String url : new String[] {postUrl(), commentUrl()}) {
            mvc.perform(post(url).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(reports.count()).isZero();
    }

    /**
     * 501자 설명으로 글을 신고하면 400을 반환하고 신고를 저장하지 않는지 검증한다.
     */
    @Test
    void rejectsOversizedReportDescription() throws Exception {
        String body = "{\"reason\":\"OTHER\",\"description\":\"" + "가".repeat(501) + "\"}";
        mvc.perform(post(postUrl()).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(reports.count()).isZero();
    }

    /**
     * 인증 없는 글·댓글 신고는 모두 401을 반환하고 신고 행을 만들지 않는지 검증한다.
     */
    @Test
    void rejectsUnauthenticatedReports() throws Exception {
        for (String url : new String[] {postUrl(), commentUrl()}) {
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        }
        assertThat(reports.count()).isZero();
    }

    /**
     * 없는 글·없는 댓글·다른 글에 속한 댓글을 신고하면 각각의 404 오류 코드를 반환하고 신고를 저장하지 않는지 검증한다.
     */
    @Test
    void rejectsUnavailableReportTargets() throws Exception {
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

    /**
     * 현재 테스트의 글 신고 URL을 저장된 게시글 ID로 구성한다.
     */
    private String postUrl() { return "/community/posts/" + postId + "/reports"; }
    /**
     * 현재 테스트의 댓글 신고 URL을 저장된 글·댓글 ID로 구성한다.
     */
    private String commentUrl() { return "/community/posts/" + postId + "/comments/" + commentId + "/reports"; }
}
