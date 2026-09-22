package com.typenull.pingdom.verification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class VisitVerificationPropertiesBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    /** 최소 필수 반경 500m와 체류 PT30S를 설정하면 context 바인딩이 성공하고 지정 값이 유지되어야 한다. */
    @Test
    void bindGlobalPolicy() {
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

    /**
     * 반경 누락 및 반경 0·체류 0인 두 설정에서 context 시작이 실패하는지 확인한다.
     * 체류 시간만 단독 누락한 경우는 이 시나리오에 포함되지 않는다.
     */
    @Test
    void rejectInvalidGlobalPolicy() {
        contextRunner.withPropertyValues(
                "verification.visit-verification.dwell-duration=PT30S"
        ).run(context -> assertThat(context).hasFailed());

        contextRunner.withPropertyValues(
                "verification.visit-verification.default-radius-meters=0",
                "verification.visit-verification.dwell-duration=PT0S"
        ).run(context -> assertThat(context).hasFailed());
    }

    /** 방문 인증 설정 Bean만 등록해 전체 애플리케이션 없이 설정 바인딩을 확인한다. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VisitVerificationProperties.class)
    static class TestConfiguration {
    }
}
