package com.typenull.pingdom.place.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.typenull.pingdom.place.api.dto.route.RouteCoordinate;
import com.typenull.pingdom.place.api.dto.route.RouteResponse;
import com.typenull.pingdom.place.application.service.route.RouteQueryService;
import com.typenull.pingdom.place.infrastructure.route.NaverDirectionsClient;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.WindowPolicy;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitUnavailableException;
import com.typenull.pingdom.shared.ratelimit.service.AbuseRateLimitService;
import com.typenull.pingdom.shared.ratelimit.service.RateLimitAspect;
import com.typenull.pingdom.shared.security.jwt.JwtAuthenticatedUser;
import com.typenull.pingdom.shared.security.refresh.RefreshTokenCookieService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

/** 전체 애플리케이션 기동 없이 JSON·AOP·공개 오류 계약을 검증합니다. */
class RouteControllerTest {
    private static final String REQUEST = "{\"origin\":{\"latitude\":37.5665,\"longitude\":126.978},\"destination\":{\"latitude\":37.4979,\"longitude\":127.0276},\"mode\":\"car\"}";
    private NaverDirectionsClient provider;
    private AbuseRateLimitService limiter;
    private MockMvc mvc;
    private JwtAuthenticatedUser user;

    @BeforeEach
    void setUp() {
        provider = mock(NaverDirectionsClient.class);
        limiter = mock(AbuseRateLimitService.class);
        user = new JwtAuthenticatedUser(7L, "route-user");
        var controller = new RouteController(new RouteQueryService(provider));
        var factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RateLimitAspect(limiter, mock(RefreshTokenCookieService.class)));
        AbuseRateLimitProperties properties = mock(AbuseRateLimitProperties.class);
        when(properties.routeQueryUser()).thenReturn(new WindowPolicy(10, Duration.ofSeconds(60)));
        when(properties.routeQueryIp()).thenReturn(new WindowPolicy(100, Duration.ofSeconds(90)));
        mvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new RouteExceptionHandler(properties), new GlobalExceptionHandler(mock(AuthMetrics.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == JwtAuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binder) {
                        return user;
                    }
                }).build();
    }

    @Test
    void returnsPayloadWithoutEnvelopeAndDoesNotCache() throws Exception {
        when(provider.findRoute(any(), any())).thenReturn(new RouteResponse("car", "naver", 12500, 1800,
                List.of(new RouteCoordinate(37.56, 126.97), new RouteCoordinate(37.49, 127.02))));
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.path[0].latitude").value(37.56))
                .andExpect(jsonPath("$.path[0].valid").doesNotExist())
                .andExpect(jsonPath("$.durationSeconds").value(1800))
                .andExpect(jsonPath("$.provider").value("naver"));
        verify(limiter).checkRouteQuery(eq(7L), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"string", "null", "missing", "overflow", "boolean", "array", "malformed", "empty-body"})
    void rejectsInvalidJsonNumbersAndBodies(String mutation) throws Exception {
        String body = switch (mutation) {
            case "string" -> REQUEST.replace("37.5665", "\"37.5665\"");
            case "null" -> REQUEST.replace("37.5665", "null");
            case "missing" -> REQUEST.replace("\"latitude\":37.5665,", "");
            case "overflow" -> REQUEST.replace("37.5665", "1e400");
            case "boolean" -> REQUEST.replace("37.5665", "true");
            case "array" -> REQUEST.replace("37.5665", "[37.5665]");
            case "empty-body" -> "";
            default -> "{";
        };
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ROUTE_REQUEST"));
        verifyNoInteractions(provider);
    }

    @Test
    void distinguishesUnsupportedMode() throws Exception {
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("car", "walk")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNSUPPORTED_ROUTE_MODE"));
        verifyNoInteractions(provider);
    }

    @Test
    void rateLimitStopsProviderAndReturnsConservativeRetryAfter() throws Exception {
        doThrow(new RateLimitException("private message")).when(limiter).checkRouteQuery(eq(7L), anyString());
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "90"))
                .andExpect(jsonPath("$.code").value("ROUTE_RATE_LIMITED"));
        verifyNoInteractions(provider);
    }

    @Test
    void preservesRateLimitStorageUnavailableContract() throws Exception {
        doThrow(new RateLimitUnavailableException(new IllegalStateException())).when(limiter).checkRouteQuery(eq(7L), anyString());
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("RATE_LIMIT_UNAVAILABLE"));
        verifyNoInteractions(provider);
    }

    @Test
    void absentAuthenticatedPrincipalCannotCallProvider() throws Exception {
        user = null;
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROUTE_NOT_FOUND", "ROUTE_PROVIDER_TIMEOUT", "ROUTE_PROVIDER_UNAVAILABLE"})
    void preservesProviderErrorContract(String code) throws Exception {
        MapErrorCode error = MapErrorCode.valueOf(code);
        when(provider.findRoute(any(), any())).thenThrow(new MapException(error));
        mvc.perform(post("/routes").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().is(error.getStatus().value())).andExpect(jsonPath("$.code").value(code));
    }
}
