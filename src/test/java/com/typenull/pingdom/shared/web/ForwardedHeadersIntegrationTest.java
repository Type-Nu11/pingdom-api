package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                // properties 파싱에서 역슬래시가 소실되지 않도록 점을 문자 클래스로 표현.
                "server.tomcat.remoteip.internal-proxies=127[.]0[.]0[.]1|::1",
                "server.tomcat.remoteip.remote-ip-header=X-Forwarded-For",
                "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto",
                "server.tomcat.remoteip.host-header=X-Forwarded-Host",
                "server.tomcat.remoteip.port-header=X-Forwarded-Port",
                "springdoc.api-docs.enabled=true",
                "springdoc.swagger-ui.enabled=true",
                "pingdom.openapi.public-access.enabled=true"
        }
)
@Import(ForwardedHeadersIntegrationTest.RequestInfoHeaderFilterConfiguration.class)
class ForwardedHeadersIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletWebServerApplicationContext applicationContext;

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 실제 Tomcat의 native RemoteIpValve와 로컬 신뢰 프록시 설정을 확인하고 전달 헤더로 외부 IP·HTTPS 보안 요청 여부가 해석되는지 검증.
     */
    @Test
    void resolvesTrustedProxyIpAndScheme() throws Exception {
        assertEquals(ServerProperties.ForwardHeadersStrategy.NATIVE, serverProperties.getForwardHeadersStrategy());
        RemoteIpValve remoteIpValve = Arrays.stream(
                        ((TomcatWebServer) applicationContext.getWebServer()).getTomcat().getEngine().getPipeline().getValves()
                )
                .filter(RemoteIpValve.class::isInstance)
                .map(RemoteIpValve.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("127[.]0[.]0[.]1|::1", remoteIpValve.getInternalProxies());

        HttpResponse<String> response = httpClient.send(forwardedRequest("/actuator/health")
                .setHeader("X-Forwarded-For", "203.0.113.250, 127.0.0.1")
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals("203.0.113.250", response.headers().firstValue("X-Test-Client-Ip").orElseThrow());
        assertEquals("true", response.headers().firstValue("X-Test-Request-Secure").orElseThrow());
    }

    /** 신뢰 프록시가 전달한 외부 host와 비표준 port를 실제 Tomcat 요청에 반영한다. */
    @Test
    void resolvesTrustedProxyHostAndPort() throws Exception {
        HttpResponse<String> response = httpClient.send(forwardedRequest("/actuator/health")
                .header("X-Forwarded-Port", "8443")
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals("www.typenull.xyz", response.headers().firstValue("X-Test-Server-Name").orElseThrow());
        assertEquals("8443", response.headers().firstValue("X-Test-Server-Port").orElseThrow());
        assertEquals("true", response.headers().firstValue("X-Test-Request-Secure").orElseThrow());
    }

    /** root와 다섯 그룹의 문서를 리다이렉트 없이 조회하고 실제 HTTPS base URL을 확인한다. */
    @Test
    void generatesHttpsServersForPublicDocuments() throws Exception {
        for (String path : new String[]{
                "/v3/api-docs", "/v3/api-docs/app", "/v3/api-docs/common",
                "/v3/api-docs/consulting", "/v3/api-docs/admin", "/v3/api-docs/merchant"
        }) {
            HttpResponse<String> response = httpClient.send(forwardedRequest(path)
                    .header("X-Forwarded-Port", "443")
                    .build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), path);
            assertNull(response.headers().firstValue("Location").orElse(null), path);
            assertEquals("https://www.typenull.xyz",
                    objectMapper.readTree(response.body()).at("/servers/0/url").asText(), path);
        }
    }

    /** 프록시 없이 로컬에서 사용하는 OpenAPI URL을 운영 도메인으로 고정하지 않는다. */
    @Test
    void keepsLocalDocumentServerWithoutForwardedHeaders() throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                .timeout(Duration.ofSeconds(20))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("http://127.0.0.1:" + port,
                objectMapper.readTree(response.body()).at("/servers/0/url").asText());
    }

    /** 같은 캐시를 사용하는 HTTP/HTTPS 동시 요청에서 요청별 서버 URL이 섞이지 않아야 한다. */
    @Test
    void keepsServerUrlsSeparateForConcurrentRequests() throws Exception {
        httpClient.send(forwardedRequest("/v3/api-docs/common").build(), HttpResponse.BodyHandlers.ofString());
        ArrayList<CompletableFuture<Void>> checks = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String scheme = i % 2 == 0 ? "https" : "http";
            checks.add(httpClient.sendAsync(forwardedRequest("/v3/api-docs/common")
                            .setHeader("X-Forwarded-Proto", scheme)
                            .header("X-Forwarded-Port", scheme.equals("https") ? "443" : "80")
                            .build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        assertEquals(200, response.statusCode());
                        try {
                            assertEquals(scheme + "://www.typenull.xyz",
                                    objectMapper.readTree(response.body()).at("/servers/0/url").asText());
                        } catch (java.io.IOException error) {
                            throw new java.io.UncheckedIOException(error);
                        }
                    }));
        }
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).get(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void preservesContractThroughActualOpenResty() throws Exception {
        OpenApiProxyTestRunner.run(port, 200);
    }

    private HttpRequest.Builder forwardedRequest(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("X-Forwarded-For", "203.0.113.250")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "www.typenull.xyz")
                .GET();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RequestInfoHeaderFilterConfiguration {

        /**
         * health 요청에서 컨테이너가 해석한 클라이언트 IP와 secure 여부를 테스트 응답 헤더에 실어 실제 웹서버 결과를 관측.
         */
        @Bean
        FilterRegistrationBean<Filter> requestInfoHeaderFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter((request, response, chain) -> {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setHeader("X-Test-Client-Ip", ClientIpResolver.resolve(httpRequest));
                httpResponse.setHeader("X-Test-Request-Secure", Boolean.toString(httpRequest.isSecure()));
                httpResponse.setHeader("X-Test-Server-Name", httpRequest.getServerName());
                httpResponse.setHeader("X-Test-Server-Port", Integer.toString(httpRequest.getServerPort()));
                chain.doFilter(request, response);
            });
            registration.addUrlPatterns("/actuator/health");
            return registration;
        }
    }
}
