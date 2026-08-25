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

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class LegacyMapApiRemovalTest {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @MethodSource("removedGetPaths")
    void removedMapGetApisReturnNotFound(String path) throws Exception {
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signupAndLogin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removedMapPostApisReturnNotFound() throws Exception {
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

    @Test
    void removedMapDeleteApisReturnNotFound() throws Exception {
        String accessToken = signupAndLogin();

        mockMvc.perform(delete("/map/posts/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/map/like/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private static Stream<String> removedGetPaths() {
        return Stream.of(
                "/map/posts",
                "/map/reports",
                "/map/place-rankings",
                "/map/bookmarks",
                "/map/likes"
        );
    }

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
