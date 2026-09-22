package com.typenull.pingdom.shared.config.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevProfileGuardConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DevProfileGuardConfig.class);

    /**
     * dev 프로필만 선택하고 별도 허용 설정이 없으면 컨텍스트 시작이 실패하는지 검증.
     */
    @Test
    void requiresExplicitDevProfileEnablement() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 활성 프로필이 비어 있으면 DevProfileGuardConfig 빈이 등록되지 않는지 검증.
     */
    @Test
    void skipsGuardWithoutDevProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=")
                .run(context -> assertThat(context).doesNotHaveBean(DevProfileGuardConfig.class));
    }

    /**
     * dev와 pingdom.dev-profile.enabled=true를 함께 설정하면 컨텍스트가 정상 시작하는지 검증.
     */
    @Test
    void startsExplicitlyEnabledDevProfile() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "pingdom.dev-profile.enabled=true"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }
}
