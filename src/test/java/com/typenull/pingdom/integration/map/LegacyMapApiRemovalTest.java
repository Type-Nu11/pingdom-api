package com.typenull.pingdom.integration.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.signup.SignupRequest;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 가입·로그인으로 인증을 통과한 뒤 구형 지도 API의 제거 상태를 검증한다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class LegacyMapApiRemovalTest {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 유효한 로그인 토큰을 보내도 제거된 구형 지도 GET 경로가 404인지 확인한다.
     */
    @ParameterizedTest
    @MethodSource("removedGetPaths")
    void removedGetRoutes(String path) throws Exception {
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signupAndLogin()))
                .andExpect(status().isNotFound());
    }

    /**
     * 구형 게시물·신고·좋아요·이의 제기 POST 경로가 인증 후에도 모두 404인지 확인한다.
     */
    @Test
    void removedPostRoutes() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(post("/map/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/map/posts/1/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/map/like")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/map/like/return/1/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/map/report-appeals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * 구형 게시물 및 좋아요 DELETE 경로가 404인지 확인한다.
     */
    @Test
    void removedDeleteRoutes() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(delete("/map/posts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/map/like/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /**
     * 제거 여부를 반복 검증할 지도 게시물·신고·랭킹·북마크·좋아요 GET 경로를 제공한다.
     */
    private static Stream<String> removedGetPaths() {
        return Stream.of(
                "/map/posts",
                "/map/reports",
                "/map/place-rankings",
                "/map/bookmarks",
                "/map/likes"
        );
    }

    /**
     * 증가하는 번호로 사용자명을 구분해 실제 가입과 로그인 API를 거친 접근 토큰을 반환한다.
     */
    private String signupAndLogin() throws Exception {
        String username = "legacy-map-removal-" + USER_SEQUENCE.incrementAndGet();
        SignupRequest signupRequest = new SignupRequest(
                username,
                username + "@example.com",
                "password123",
                1998,
                null,
                "ko",
                "KR"
        );

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();
    }
}
