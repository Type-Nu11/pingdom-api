package com.typenull.pingdom.shared.config.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** dev 프로필 사용 시 별도 허용 플래그가 없으면 시작을 중단해 개발 설정의 우발적 활성화를 차단. */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class DevProfileGuardConfig {

    public DevProfileGuardConfig(
            @Value("${pingdom.dev-profile.enabled:false}") boolean devProfileEnabled
    ) {
        if (!devProfileEnabled) {
            throw new IllegalStateException(
                    "The dev profile exposes Swagger and development seed settings. "
                            + "Set PINGDOM_DEV_PROFILE_ENABLED=true only in a development environment."
            );
        }
    }
}
