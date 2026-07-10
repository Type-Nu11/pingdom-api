package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                "server.tomcat.remoteip.internal-proxies=127\\\\.0\\\\.0\\\\.1|::1",
                "server.tomcat.remoteip.remote-ip-header=X-Forwarded-For",
                "server.tomcat.remoteip.protocol-header=X-Forwarded-Proto"
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

    @Test
    void trustedProxyHeadersResolveClientIpAndExternalHttpsScheme() {
        assertEquals(ServerProperties.ForwardHeadersStrategy.NATIVE, serverProperties.getForwardHeadersStrategy());
        RemoteIpValve remoteIpValve = Arrays.stream(
                        ((TomcatWebServer) applicationContext.getWebServer()).getTomcat().getEngine().getPipeline().getValves()
                )
                .filter(RemoteIpValve.class::isInstance)
                .map(RemoteIpValve.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("127\\.0\\.0\\.1|::1", remoteIpValve.getInternalProxies());

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.250, 127.0.0.1");
        headers.add("X-Forwarded-Proto", "https");

        ResponseEntity<String> response = new TestRestTemplate().exchange(
                "http://127.0.0.1:" + port + "/actuator/health",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals("203.0.113.250", response.getHeaders().getFirst("X-Test-Client-Ip"));
        assertEquals("true", response.getHeaders().getFirst("X-Test-Request-Secure"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RequestInfoHeaderFilterConfiguration {

        @Bean
        FilterRegistrationBean<Filter> requestInfoHeaderFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
            registration.setFilter((request, response, chain) -> {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setHeader("X-Test-Client-Ip", ClientIpResolver.resolve(httpRequest));
                httpResponse.setHeader("X-Test-Request-Secure", Boolean.toString(httpRequest.isSecure()));
                chain.doFilter(request, response);
            });
            registration.addUrlPatterns("/actuator/health");
            return registration;
        }
    }
}
