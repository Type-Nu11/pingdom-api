package com.typenull.pingdom.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.junit.jupiter.api.Tag;
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

@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1|::1",
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

    /**
     * 실제 Tomcat의 native RemoteIpValve와 로컬 신뢰 프록시 설정을 확인하고 전달 헤더로 외부 IP·HTTPS 보안 요청 여부가 해석되는지 검증한다.
     */
    @Test
    void resolvesTrustedProxyIpAndScheme() {
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

        /**
         * health 요청에서 컨테이너가 해석한 클라이언트 IP와 secure 여부를 테스트 응답 헤더에 실어 실제 웹서버 결과를 관측한다.
         */
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
