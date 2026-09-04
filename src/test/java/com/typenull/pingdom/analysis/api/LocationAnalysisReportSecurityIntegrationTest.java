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

    @Test
    void rejectsUnauthenticatedLocationAnalysisRequest() throws Exception {
        assertUnauthorized(post("/analysis/reports/location"));
        assertUnauthorized(get("/analysis/reports").param("email", "owner@example.com"));
        assertUnauthorized(get("/analysis/reports/report-1").param("email", "owner@example.com"));
        assertUnauthorized(get("/analysis/reports/report-1/download").param("email", "owner@example.com"));
        assertUnauthorized(get("/analysis/reports/report-1/html").param("email", "owner@example.com"));
        assertUnauthorized(patch("/analysis/reports/report-1").param("email", "owner@example.com"));
        assertUnauthorized(delete("/analysis/reports/report-1").param("email", "owner@example.com"));
    }

    @Test
    void rejectsAnotherUsersEmailForArchiveLookup() throws Exception {
        User user = createUser("reportOwner", "owner@example.com");

        mockMvc.perform(get("/analysis/reports")
                        .param("email", "other@example.com")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ANALYSIS_REPORT_FORBIDDEN"));
    }

    @Test
    void rejectsAnotherUsersEmailBeforeGeneratingReport() throws Exception {
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

    @Test
    void rejectsChangingReportEmailToAnotherUsersEmail() throws Exception {
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

    @Test
    void allowsAuthenticatedUserToLookUpOwnEmail() throws Exception {
        User user = createUser("reportReader", "reader@example.com");

        mockMvc.perform(get("/analysis/reports")
                        .param("email", " READER@EXAMPLE.COM ")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

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

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole().name());
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
