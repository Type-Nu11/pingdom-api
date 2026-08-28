package com.typenull.pingdom.shared.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Profile;

class SpringdocProfileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SpringdocSecurityConfig.class,
                    PlaceExplorationOpenApiConfig.class,
                    SpringdocGroupsConfig.class
            );

    @Test
    void groupedOpenApiConfigurationsAreNotProfileRestricted() {
        assertThat(SpringdocGroupsConfig.class.getAnnotation(Profile.class)).isNull();
        assertThat(PlaceExplorationOpenApiConfig.class.getAnnotation(Profile.class)).isNull();
    }

    @Test
    void bearerSecurityConfigurationIsNotProfileRestricted() {
        assertThat(SpringdocSecurityConfig.class.getAnnotation(Profile.class)).isNull();
    }

    @Test
    void defaultContextRegistersAllGroupedOpenApiBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("appApi");
            assertThat(context).hasBean("commonApi");
            assertThat(context).hasBean("consultingApi");
            assertThat(context).hasBean("adminApi");
            assertThat(context).hasBean("merchantApi");
        });
    }
}
