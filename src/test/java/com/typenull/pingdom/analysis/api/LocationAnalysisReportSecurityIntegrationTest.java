package com.typenull.pingdom.analysis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.analysis.domain.LocationAnalysisReport;
import com.typenull.pingdom.analysis.infrastructure.LocationAnalysisReportRepository;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationAnalysisReportSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private LocationAnalysisReportRepository reportRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void blocksCaseVariantAccountAcrossEveryReportOperation() throws Exception {
        User owner = createUser("caseOwner", "Owner@example.com");
        User other = createUser("caseOther", "owner@example.com");
        saveReport("case-report", owner);

        assertAllOperationsDenyReport("case-report", other);
        assertThat(reportRepository.findById("case-report")).isPresent();
    }

    @Test
    void doesNotTransferReportToReregisteredEmailAccount() throws Exception {
        User withdrawn = createUser("formerOwner", "reused@example.com");
        saveReport("former-report", withdrawn);
        withdrawn.withdraw("withdrawn_" + withdrawn.getId(),
                "withdrawn_" + withdrawn.getId() + "@withdrawn.local", "encoded-password", LocalDateTime.now());
        userRepository.saveAndFlush(withdrawn);
        User replacement = createUser("replacementOwner", "reused@example.com");

        assertAllOperationsDenyReport("former-report", replacement);
        assertThat(reportRepository.findById("former-report").orElseThrow().getOwnerUserId())
                .isEqualTo(withdrawn.getId());
    }

    @Test
    void quarantinesLegacyReportEvenForMatchingAccountEmail() throws Exception {
        User owner = createUser("legacyOwner", "legacy@example.com");
        saveReport("legacy-report", owner);
        jdbcTemplate.update("UPDATE location_analysis_report SET owner_user_id = NULL WHERE report_id = ?", "legacy-report");
        entityManager.clear();

        assertAllOperationsDenyReport("legacy-report", owner);
        assertThat(reportRepository.findById("legacy-report").orElseThrow().getOwnerUserId()).isNull();
    }

    @Test
    void allowsOwnerAcrossReadUpdateAndDeleteAfterAccountEmailChange() throws Exception {
        User owner = createUser("currentOwner", "before@example.com");
        saveReport("owned-report", owner);
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?", "after@example.com", owner.getId());
        entityManager.clear();
        User currentOwner = userRepository.findById(owner.getId()).orElseThrow();
        String token = bearerToken(currentOwner);

        mockMvc.perform(get("/analysis/reports").param("email", "after@example.com")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports[0].reportId").value("owned-report"));
        mockMvc.perform(get("/analysis/reports/owned-report").param("email", "after@example.com")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/analysis/reports/owned-report/download").param("email", "after@example.com")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2}));
        mockMvc.perform(get("/analysis/reports/owned-report/html").param("email", "after@example.com")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(content().string("<html>원문</html>"));
        mockMvc.perform(patch("/analysis/reports/owned-report").param("email", "after@example.com")
                        .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("reportName", "수정", "email", "after@example.com"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reportName").value("수정"));
        assertThat(reportRepository.findById("owned-report").orElseThrow().getOwnerUserId()).isEqualTo(owner.getId());
        mockMvc.perform(delete("/analysis/reports/owned-report").param("email", "after@example.com")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(reportRepository.findById("owned-report")).isEmpty();
    }

    private void assertAllOperationsDenyReport(String reportId, User user) throws Exception {
        String token = bearerToken(user);
        String email = user.getEmail();
        mockMvc.perform(get("/analysis/reports").param("email", email)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reports.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
        for (String suffix : new String[]{"", "/download", "/html"}) {
            mockMvc.perform(get("/analysis/reports/" + reportId + suffix).param("email", email)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_NOT_FOUND"));
        }
        mockMvc.perform(patch("/analysis/reports/" + reportId).param("email", email)
                        .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("reportName", "탈취", "email", email))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_NOT_FOUND"));
        mockMvc.perform(delete("/analysis/reports/" + reportId).param("email", email)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_NOT_FOUND"));
    }

    private void saveReport(String reportId, User owner) {
        reportRepository.saveAndFlush(LocationAnalysisReport.create(reportId, owner.getId(), "분석", "카페", "서울",
                null, null, owner.getEmail().toLowerCase(java.util.Locale.ROOT), true, LocalDate.now(), LocalDate.now(),
                "<html>원문</html>", new byte[]{1, 2}, LocalDateTime.now()));
    }

    /**
     * 인증 없는 보고서 생성·목록·상세·다운로드·HTML·수정·삭제 요청을 모두 401 INVALID_TOKEN으로 거절하는지 검증.
     */
    @Test
    void rejectsUnauthenticatedReportRequests() throws Exception {
        assertUnauthorized(post("/analysis/reports/location"));
        assertUnauthorized(get("/analysis/reports").param("email", "owner@example.com"));
        assertUnauthorized(get("/analysis/reports/report-1").param("email", "owner@example.com"));
        assertUnauthorized(get("/analysis/reports/report-1/download").param("email", "owner@example.com"));
        assertUnauthorized(get("/analysis/reports/report-1/html").param("email", "owner@example.com"));
        assertUnauthorized(patch("/analysis/reports/report-1").param("email", "owner@example.com"));
        assertUnauthorized(delete("/analysis/reports/report-1").param("email", "owner@example.com"));
    }

    /**
     * 인증 사용자와 다른 이메일의 보관 목록 조회가 403 ANALYSIS_REPORT_FORBIDDEN인지 검증.
     */
    @Test
    void rejectsUnownedArchiveEmail() throws Exception {
        User user = createUser("reportOwner", "owner@example.com");

        mockMvc.perform(get("/analysis/reports")
                        .param("email", "other@example.com")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_FORBIDDEN"));
    }

    /**
     * PDF 생성 요청의 이메일이 로그인 계정과 다르면 403 ANALYSIS_REPORT_FORBIDDEN인지 검증.
     */
    @Test
    void rejectsUnownedGenerationEmail() throws Exception {
        User user = createUser("reportGenerator", "generator@example.com");

        mockMvc.perform(post("/analysis/reports/location")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .accept(MediaType.APPLICATION_PDF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "region", "서울 강남구",
                                "category", "카페",
                                "email", "other@example.com",
                                "privacyConsent", true
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_FORBIDDEN"));
    }

    /**
     * 본인 이메일로 접근해도 수정 본문을 타인 이메일로 바꾸면 403 ANALYSIS_REPORT_FORBIDDEN인지 검증.
     */
    @Test
    void rejectsUnownedReportEmailChange() throws Exception {
        User user = createUser("reportEditor", "editor@example.com");

        mockMvc.perform(patch("/analysis/reports/report-1")
                        .param("email", "editor@example.com")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "reportName", "수정된 보고서",
                                "email", "other@example.com"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_FORBIDDEN"));
    }

    /**
     * 본인 이메일의 대소문자·양끝 공백 차이를 허용하고 보관 보고서가 없으면 빈 페이지를 반환하는지 검증.
     */
    @Test
    void allowsNormalizedOwnedEmail() throws Exception {
        User user = createUser("reportReader", "reader@example.com");

        mockMvc.perform(get("/analysis/reports")
                        .param("email", " READER@EXAMPLE.COM ")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reports").isArray())
                .andExpect(jsonPath("$.reports.length()").value(0))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    /**
     * 주어진 이름·이메일의 일반 사용자를 저장해 JWT와 이메일 소유 검증의 실제 입력으로 사용.
     */
    private User createUser(String username, String email) {
        return userRepository.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .password("encoded-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
    }

    /**
     * 사용자의 ID·이름·역할로 액세스 JWT를 발급하고 Bearer 인증 헤더를 구성.
     */
    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
    }

    /**
     * 인증 정보 없는 요청이 401과 INVALID_TOKEN 코드를 반환하는지 공통으로 확인.
     */
    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
