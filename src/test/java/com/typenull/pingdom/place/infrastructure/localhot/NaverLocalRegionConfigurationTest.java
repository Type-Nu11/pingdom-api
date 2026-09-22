package com.typenull.pingdom.place.infrastructure.localhot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class NaverLocalRegionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NaverLocalRegionConfiguration.class, TestConfiguration.class)
            .withPropertyValues(
                    "place.local-hot.naver.enabled=true",
                    "place.local-hot.naver.client-id=test-client-id",
                    "place.local-hot.naver.client-secret=test-client-secret",
                    "place.local-hot.naver.base-url=https://naver.test",
                    "place.local-hot.naver.connect-timeout=PT1S",
                    "place.local-hot.naver.read-timeout=PT2S",
                    "place.local-hot.naver.cache-ttl=PT5M",
                    "place.local-hot.naver.cache-max-entries=100"
            );

    /** 네이버 인증·URL·타임아웃·캐시 설정이 바인딩되고 명시된 이름의 RestClient 빈이 생성되는지 확인한다. */
    @Test
    void bindsNaverClientConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("naverLocalRegionRestClient");

            NaverLocalRegionProperties properties = context.getBean(NaverLocalRegionProperties.class);
            assertThat(properties.isConfigured()).isTrue();
            assertThat(properties.clientId()).isEqualTo("test-client-id");
            assertThat(properties.clientSecret()).isEqualTo("test-client-secret");
            assertThat(properties.baseUrl()).isEqualTo("https://naver.test");
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.cacheTtl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.cacheMaxEntries()).isEqualTo(100);
            assertThat(context.getBean("naverLocalRegionRestClient")).isInstanceOf(RestClient.class);
        });
    }

    /** 비밀키 누락과 기능 비활성은 configured=false이며 생략한 URL·타임아웃·캐시에는 기본값이 적용되는지 확인한다. */
    @Test
    void requiresEnabledNaverCredentials() {
        NaverLocalRegionProperties missingSecret =
                new NaverLocalRegionProperties(true, "client-id", null, null, null, null, null, null);

        assertThat(missingSecret.isConfigured()).isFalse();
        assertThat(missingSecret.baseUrl()).isEqualTo("https://naveropenapi.apigw.ntruss.com");
        assertThat(missingSecret.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(missingSecret.readTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(missingSecret.cacheTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(missingSecret.cacheMaxEntries()).isEqualTo(10_000);
        assertThat(new NaverLocalRegionProperties(false, "client-id", "client-secret", null, null, null, null, null)
                .isConfigured()).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(NaverLocalRegionProperties.class)
    static class TestConfiguration {
    }
}
