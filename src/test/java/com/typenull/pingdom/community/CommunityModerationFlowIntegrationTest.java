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

    /**
     * 작성자·신고자·관리자와 신고 대상 글·댓글을 저장하고 신고 및 심사 요청의 역할별 토큰을 준비.
     */
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

    /**
     * 글 신고의 접수·관리자 목록 및 상세 조회·수락을 연결하고 대상이 숨김 상태로 저장되는지 검증.
     * 관리자는 숨김 글을 조회할 수 있고 일반 조회는 POST_NOT_FOUND로 차단되는지도 확인.
     */
    @Test
    void hidesPostAfterReportAcceptance() throws Exception {
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

    /**
     * 댓글 신고를 반려하면 DECLINED 상태와 숨김 false를 반환하고 댓글이 일반 목록과 관리자 신고 목록에 유지되는지 검증.
     */
    @Test
    void preservesCommentAfterReportDecline() throws Exception {
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

    /**
     * 미인증 신고·일반 사용자의 관리자 접근·중복 신고·처리된 신고 재심사·없는 신고 조회의 HTTP 상태와 도메인 오류 코드를 검증.
     */
    @Test
    void enforcesReportModerationErrorContracts() throws Exception {
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

    /**
     * 방금 접수한 신고를 심사할 수 있도록 저장된 신고 중 가장 큰 식별자를 찾음.
     */
    private long reportId() {
        return reportRepository.findAll().stream()
                .max(Comparator.comparing(CommunityReport::getId))
                .orElseThrow()
                .getId();
    }

    /**
     * 준비한 게시글 ID로 일반 사용자의 글 신고 경로를 구성.
     */
    private String postReportPath() {
        return "/community/posts/" + postId + "/reports";
    }

    /**
     * 준비한 게시글과 댓글 ID로 댓글 신고 경로를 구성.
     */
    private String commentReportPath() {
        return "/community/posts/" + postId + "/comments/" + commentId + "/reports";
    }

    /**
     * 역할별 인증과 권한 차이를 검증할 이메일 인증 완료 사용자를 생성.
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
     * 신고자 또는 관리자의 현재 역할을 포함하는 Bearer 인증 헤더를 생성.
     */
    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }
}
