package com.typenull.pingdom.analysis.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
     * 본인 이메일의 대소문자·양끝 공백 차이를 허용하고 보관 보고서가 없으면 200 빈 배열을 반환하는지 검증.
     */
    @Test
    void allowsNormalizedOwnedEmail() throws Exception {
        User user = createUser("reportReader", "reader@example.com");

        mockMvc.perform(get("/analysis/reports")
                        .param("email", " READER@EXAMPLE.COM ")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
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
