package com.typenull.pingdom.shared.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class SpringdocProfileConfigurationTest {

    @Test
    void localProfileRegistersGroupedOpenApiConfiguration() {
        assertThat(profilesOf(SpringdocGroupsConfig.class)).contains("local");
        assertThat(profilesOf(PlaceExplorationOpenApiConfig.class)).contains("local");
    }

    @Test
    void bearerSecurityConfigurationIsNotProfileRestricted() {
        assertThat(SpringdocSecurityConfig.class.getAnnotation(Profile.class)).isNull();
    }

    private String[] profilesOf(Class<?> configurationClass) {
        Profile profile = configurationClass.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        return Arrays.stream(profile.value()).toArray(String[]::new);
    }
}
