package com.typenull.pingdom.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserRole;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 실제 Security 체인에서 미등록 페이지만 이동하고 기존 API의 JSON 인증 오류는 유지되는지 검증. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class UnknownPathRedirectSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserAccessStatusService userAccessStatusService;

    @ParameterizedTest
    @ValueSource(strings = {"/unknown", "/unknown/deep", "/users-typo", "/admin/unknown", "/auth/unknown"})
    void redirectsUnknownPages(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/console/login"));
    }

    @Test
    void redirectsHeadWithInvalidToken() throws Exception {
        mockMvc.perform(head("/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/console/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/users/me", "/places/42", "/admin/places"})
    void registeredApisKeepUnauthorizedJson(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void registeredPublicApiKeepsMethodValidation() throws Exception {
        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    @Transactional
    void registeredAdminApiKeepsForbiddenJson() throws Exception {
        User user = ordinaryUser();
        try {
            mockMvc.perform(get("/admin/places")
                            .header(HttpHeaders.AUTHORIZATION, accessToken(user)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        } finally {
            userAccessStatusService.evict(user.getId());
        }
    }

    @Test
    void registeredPublicApiKeepsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    @Transactional
    void registeredGetApiKeepsNotAcceptableMediaType() throws Exception {
        User user = ordinaryUser();
        try {
            mockMvc.perform(get("/analysis/reports/unused-report/download")
                            .header(HttpHeaders.AUTHORIZATION, accessToken(user))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotAcceptable())
                    .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        } finally {
            userAccessStatusService.evict(user.getId());
        }
    }

    @Test
    void consoleLoginDoesNotRedirectToItself() throws Exception {
        // 로그인 화면은 프론트에서 제공. 백엔드로 직접 요청해도 동일 주소로 반복 이동시키지 않음.
        mockMvc.perform(get("/console/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void rootApiKeepsExistingResponse() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Pingdom Backend is running."));
    }

    private User ordinaryUser() {
        return userRepository.saveAndFlush(User.builder()
                .username("path-index-user")
                .email("path-index-user@example.com")
                .password("unused-test-password")
                .birthYear(1998)
                .language("ko")
                .country("KR")
                .role(UserRole.USER)
                .build());
    }

    private String accessToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), "USER");
    }
}
