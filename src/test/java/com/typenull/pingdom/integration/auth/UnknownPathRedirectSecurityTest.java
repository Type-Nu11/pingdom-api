package com.typenull.pingdom.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Security 체인에서 미등록 페이지만 이동하고 기존 API의 JSON 인증 오류는 유지되는지 검증. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class UnknownPathRedirectSecurityTest {

    @Autowired
    private MockMvc mockMvc;

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
}
