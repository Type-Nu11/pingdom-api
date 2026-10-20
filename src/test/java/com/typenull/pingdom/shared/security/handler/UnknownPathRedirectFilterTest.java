package com.typenull.pingdom.shared.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

class UnknownPathRedirectFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void redirectsUnknownPageBeforeAuthentication(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/unknown/deep");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        request.setQueryString("next=https://example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter(true).doFilter(request, response, (req, res) -> continued.set(true));

        assertThat(continued).isFalse();
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/console/login");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void keepsRegisteredPathRegardlessOfMethodHeadersAndParameters(boolean parsedPatterns) throws Exception {
        // 실제 매핑은 POST + JSON + 필수 파라미터. GET이어도 API 경로이므로 보안·MVC가 오류를 결정.
        assertContinues(filter(parsedPatterns), new MockHttpServletRequest("GET", "/places/42"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void keepsNonPageRequests(String method) throws Exception {
        assertContinues(filter(true), new MockHttpServletRequest(method, "/unknown"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/console/login", "/console/assets/app.js", "/error", "/actuator/health/readiness",
            "/actuator/metrics", "/swagger-ui", "/swagger-ui.html", "/swagger-ui/index.html",
            "/v3/api-docs", "/v3/api-docs/app"
    })
    void keepsConsoleAndFrameworkEndpoints(String path) throws Exception {
        assertContinues(filter(true), new MockHttpServletRequest("GET", path));
    }

    @Test
    void recognizesApiWithinContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/places/42");
        request.setContextPath("/app");

        assertContinues(filter(true), request);
    }

    private UnknownPathRedirectFilter filter(boolean parsedPatterns) throws Exception {
        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(parsedPatterns ? new PathPatternParser() : null);
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.registerMapping(RequestMappingInfo.paths("/places/{placeId}")
                        .methods(RequestMethod.POST).consumes("application/json").params("required")
                        .options(options).build(),
                this, getClass().getDeclaredMethod("mappedEndpoint"));
        return new UnknownPathRedirectFilter(() -> mapping);
    }

    private void assertContinues(UnknownPathRedirectFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertThat(continued).isTrue();
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
    }

    private void mappedEndpoint() {
    }
}
