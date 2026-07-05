package com.typenull.pingdom.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.identity.api.dto.login.LoginRequest;
import com.typenull.pingdom.identity.api.dto.token.RefreshTokenRequest;
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

    @Test
    void refreshedAccessTokenCanAccessProtectedApis() throws Exception {
        createUser("refreshMatrixUser");

        String refreshToken = loginAndReadToken("refreshMatrixUser", "refreshToken");
        MvcResult refreshResult = mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
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

        String refreshToken = loginAndReadToken("refreshPlaceQueryUser", "refreshToken");
        MvcResult refreshResult = mockMvc.perform(post("/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
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

    private String loginAndReadToken(String username, String tokenName) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get(tokenName)
                .textValue();
    }
}
