package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.shared.config.proxy.OiletProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NaverPlaceSearchConfigurationTest {

    @Test
    void wiresBothSearchClientsWithSeparateConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(SearchConfiguration.class)
                .withPropertyValues(
                        "place.registration.naver-search.enabled=true",
                        "place.registration.naver-search.client-id=place-id",
                        "place.registration.naver-search.client-secret=place-secret",
                        "place.registration.naver-geocoding.enabled=true",
                        "place.registration.naver-geocoding.client-id=address-id",
                        "place.registration.naver-geocoding.client-secret=address-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(NaverPlaceSearchClient.class)
                            .hasSingleBean(NaverAddressSearchClient.class);
                    assertThat(context.getBean("naverPlaceSearchRestClient"))
                            .isNotSameAs(context.getBean("naverAddressSearchRestClient"));
                    assertThat(context.getBean(NaverPlaceSearchClient.Properties.class).isConfigured()).isTrue();
                    assertThat(context.getBean(NaverAddressSearchClient.Properties.class).isConfigured()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({NaverPlaceSearchClient.Properties.class, NaverAddressSearchClient.Properties.class,
            OiletProxyProperties.class})
    @Import({NaverPlaceSearchConfiguration.class, NaverPlaceSearchClient.class,
            NaverAddressSearchConfiguration.class, NaverAddressSearchClient.class})
    static class SearchConfiguration {}
}
