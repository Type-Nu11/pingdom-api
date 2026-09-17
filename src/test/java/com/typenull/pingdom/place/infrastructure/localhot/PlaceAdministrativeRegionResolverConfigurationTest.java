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

    @Test
    void 네이버_지역_조회가_활성화되면_네이버_Resolver만_등록한다() {
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

    @Test
    void 네이버_지역_조회가_비활성화되면_기존_카카오_Resolver를_유지한다() {
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
