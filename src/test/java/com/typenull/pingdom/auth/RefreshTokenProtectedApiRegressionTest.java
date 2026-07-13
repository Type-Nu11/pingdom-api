package com.typenull.pingdom.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import jakarta.servlet.http.Cookie;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenProtectedApiRegressionTest extends AuthRegressionIntegrationTestSupport {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "PINGDOM_REFRESH_TOKEN";

    @Test
    void refreshedAccessTokenCanAccessProtectedApis() throws Exception {
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

    @Test
    void refreshedAccessTokenCanAccessPlaceWithPaginationQueryParameters() throws Exception {
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

    private static Stream<String> protectedGetEndpoints() {
        return Stream.of("/places", "/map/posts", "/users/me");
    }

    private String loginAndReadRefreshToken(String username) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk());

        return userRepository.findByUsername(username)
                .orElseThrow()
                .getRefreshToken();
    }
}
