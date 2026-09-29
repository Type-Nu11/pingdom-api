package com.typenull.pingdom.integration.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.place.api.dto.route.RouteResponse;
import com.typenull.pingdom.place.infrastructure.route.NaverDirectionsClient;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;
import com.typenull.pingdom.shared.security.access.UserAccessStatusService;
import com.typenull.pingdom.shared.security.jwt.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 JWT 필터·AOP·OpenAPI 연결을 확인하며 외부 공급자와 Redis는 호출하지 않습니다. */
@Tag("integration")
@SpringBootTest(properties = "pingdom.openapi.public-access.enabled=true")
@AutoConfigureMockMvc
class RouteApiIntegrationTest {
    private static final String REQUEST = "{\"origin\":{\"latitude\":37.5665,\"longitude\":126.978},\"destination\":{\"latitude\":37.4979,\"longitude\":127.0276},\"mode\":\"car\"}";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtTokenProvider tokens;
    @MockBean private NaverDirectionsClient provider;
    @MockBean private UserAccessStatusService accessStatus;
    @MockBean private RateLimitStore limits;

    @BeforeEach
    void setUp() {
        when(accessStatus.canAuthenticate(7L)).thenReturn(true);
    }

    @Test
    void requiresJwtBeforeCallingLimiterOrProvider() throws Exception {
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verifyNoInteractions(provider, limits);
    }

    @Test
    void rejectsRefreshToken() throws Exception {
        mvc.perform(post("/routes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateRefreshToken(7L))
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(provider, limits);
    }

    @Test
    void acceptsValidJwtWithoutDeviceHeadersOrRedirects() throws Exception {
        when(provider.findRoute(any(), any())).thenReturn(new RouteResponse("car", "naver", 12500, 1800,
                List.of(new RouteCoordinate(37.56, 126.97), new RouteCoordinate(37.49, 127.02))));
        mvc.perform(post("/routes").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.mode").value("car")).andExpect(jsonPath("$.durationSeconds").value(1800));
        verify(limits).acquire(anyString(), argThat(rules -> rules.stream().anyMatch(rule -> rule.key().equals("route-query:user:7"))), anyCollection());
    }

    @Test
    void limitsRequestsBeforeProviderCall() throws Exception {
        doThrow(new RateLimitException("limit")).when(limits).acquire(anyString(), anyCollection(), anyCollection());
        mvc.perform(post("/routes").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("ROUTE_RATE_LIMITED"));
        verifyNoInteractions(provider);
    }

    @Test
    void documentsRouteInAppWithUnitsAuthenticationAndErrors() throws Exception {
        JsonNode app = mapper.readTree(mvc.perform(get("/v3/api-docs/app")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        JsonNode operation = app.at("/paths/~1routes/post");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        for (String status : List.of("200", "400", "401", "403", "422", "429", "503", "504")) {
            assertThat(operation.path("responses").has(status)).as(status).isTrue();
        }
        assertThat(app.at("/components/schemas/RouteResponse/properties/durationSeconds/description").asText()).contains("초");
        assertThat(app.at("/components/schemas/RouteCoordinate/properties/latitude/type").asText()).isEqualTo("number");
        JsonNode common = mapper.readTree(mvc.perform(get("/v3/api-docs/common")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(common.path("paths").has("/routes")).isFalse();
    }

    private String bearer() {
        return "Bearer " + tokens.generateAccessToken(7L, "route-user", "USER");
    }
}
