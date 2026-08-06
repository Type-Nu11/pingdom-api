package com.typenull.pingdom.shared.config.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DevProfileGuardConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DevProfileGuardConfig.class);

    @Test
    void dev_프로필은_명시적_활성화_환경_변수가_필요하다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 활성_프로필이_비어_있으면_dev_프로필_가드를_등록하지_않는다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=")
                .run(context -> assertThat(context).doesNotHaveBean(DevProfileGuardConfig.class));
    }

    @Test
    void dev_프로필은_명시적으로_활성화하면_시작할_수_있다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "pingdom.dev-profile.enabled=true"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }
}
