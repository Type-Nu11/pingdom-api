package com.typenull.pingdom.place.infrastructure.localhot;

import static org.assertj.core.api.Assertions.assertThat;

import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class PlaceAdministrativeRegionResolverConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    /** 네이버 조회 활성 시 지역 resolver 빈이 하나이며 네이버 구현체인지 확인한다. */
    @Test
    void selectsEnabledNaverResolver() {
        contextRunner.withPropertyValues(
                "place.local-hot.naver.enabled=true",
                "place.local-hot.naver.client-id=test-client-id",
                "place.local-hot.naver.client-secret=test-client-secret"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(PlaceAdministrativeRegionResolver.class))
                    .hasSize(1)
                    .allSatisfy((beanName, resolver) ->
                            assertThat(resolver).isInstanceOf(NaverPlaceAdministrativeRegionResolver.class));
        });
    }

    /** 네이버 조회 비활성 시 지역 resolver를 하나만 등록하고 기존 Kakao 구현체를 선택하는지 확인한다. */
    @Test
    void fallsBackToKakaoResolver() {
        contextRunner.withPropertyValues("place.local-hot.naver.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PlaceAdministrativeRegionResolver.class))
                            .hasSize(1)
                            .allSatisfy((beanName, resolver) ->
                                    assertThat(resolver).isInstanceOf(KakaoPlaceAdministrativeRegionResolver.class));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({KakaoLocalRegionProperties.class, NaverLocalRegionProperties.class})
    @Import({
            KakaoLocalRegionConfiguration.class,
            NaverLocalRegionConfiguration.class,
            KakaoPlaceAdministrativeRegionResolver.class,
            NaverPlaceAdministrativeRegionResolver.class
    })
    static class TestConfiguration {
    }
}
