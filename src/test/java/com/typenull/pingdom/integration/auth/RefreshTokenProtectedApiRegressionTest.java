package com.typenull.pingdom.integration.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그인 갱신 쿠키로 받은 접근 토큰이 실제 보호 API에서도 사용 가능한지 검증한다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenProtectedApiRegressionTest extends AuthRegressionIntegrationTestSupport {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "PINGDOM_REFRESH_TOKEN";

    /**
     * 로그인 쿠키로 갱신한 접근 토큰이 장소·내 정보 API에 사용되고 갱신 토큰은 응답 본문에 없는지 확인한다.
     */
    @Test
    void refreshThenProtectedApis() throws Exception {
        createUser("refreshMatrixUser");

        String refreshToken = loginAndReadRefreshToken("refreshMatrixUser");
        MvcResult refreshResult = mockMvc.perform(post("/auth/token/refresh")
                        .cookie(new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String refreshedAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();

        for (String endpoint : protectedGetEndpoints().toList()) {
            mockMvc.perform(get(endpoint)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedAccessToken))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 갱신된 접근 토큰으로 page와 limit을 포함한 장소 목록 조회도 성공하는지 확인한다.
     */
    @Test
    void refreshThenPlacePage() throws Exception {
        createUser("refreshPlaceQueryUser");

        String refreshToken = loginAndReadRefreshToken("refreshPlaceQueryUser");
        MvcResult refreshResult = mockMvc.perform(post("/auth/token/refresh")
                        .cookie(new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        String refreshedAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken")
                .textValue();

        mockMvc.perform(get("/places")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedAccessToken)
                        .param("limit", "100")
                        .param("page", "1"))
                .andExpect(status().isOk());
    }

    /**
     * 갱신 이후 접근을 확인할 장소 목록과 내 정보 경로를 제공한다.
     */
    private static Stream<String> protectedGetEndpoints() {
        return Stream.of("/places", "/users/me");
    }

    /**
     * 실제 로그인 성공 후 PINGDOM_REFRESH_TOKEN 쿠키에서 갱신 토큰을 추출한다.
     */
    private String loginAndReadRefreshToken(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return loginResult.getResponse().getCookie(REFRESH_TOKEN_COOKIE_NAME).getValue();
    }
}
