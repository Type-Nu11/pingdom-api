package com.typenull.pingdom.shared.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class SpringdocProfileConfigurationTest {

    @Test
    void localProfileRegistersGroupedOpenApiAndBearerSecurityConfiguration() {
        assertThat(profilesOf(SpringdocGroupsConfig.class)).contains("local");
        assertThat(profilesOf(SpringdocSecurityConfig.class)).contains("local");
        assertThat(profilesOf(PlaceExplorationOpenApiConfig.class)).contains("local");
    }

    private String[] profilesOf(Class<?> configurationClass) {
        Profile profile = configurationClass.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        return Arrays.stream(profile.value()).toArray(String[]::new);
    }
}
