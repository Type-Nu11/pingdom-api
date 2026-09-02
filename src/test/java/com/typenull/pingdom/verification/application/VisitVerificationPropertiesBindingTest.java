package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class VisitVerificationPropertiesBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsConfiguredGlobalPolicy() {
        contextRunner.withPropertyValues(
                "verification.visit-verification.default-radius-meters=500",
                "verification.visit-verification.dwell-duration=PT30S"
        ).run(context -> {
            assertThat(context).hasNotFailed();

            VisitVerificationProperties properties = context.getBean(VisitVerificationProperties.class);
            assertThat(properties.defaultRadiusMeters()).isEqualTo(500.0);
            assertThat(properties.dwellDuration()).isEqualTo(java.time.Duration.ofSeconds(30));
        });
    }

    @Test
    void failsWhenGlobalRadiusOrDwellDurationIsMissingOrInvalid() {
        contextRunner.withPropertyValues(
                "verification.visit-verification.dwell-duration=PT30S"
        ).run(context -> assertThat(context).hasFailed());

        contextRunner.withPropertyValues(
                "verification.visit-verification.default-radius-meters=0",
                "verification.visit-verification.dwell-duration=PT0S"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VisitVerificationProperties.class)
    static class TestConfiguration {
    }
}
