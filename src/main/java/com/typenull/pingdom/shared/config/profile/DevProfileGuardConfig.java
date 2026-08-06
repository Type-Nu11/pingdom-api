package com.typenull.pingdom.shared.config.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
