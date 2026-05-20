package com.typenull.pingdom.global.config.swagger;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class SpringdocGroupsConfig {

    @Bean
    public GroupedOpenApi appApi() {
        return GroupedOpenApi.builder()
                .group("app")
                .pathsToMatch(
                        "/auth/**",
                        "/users/**",
                        "/map/**"
                )
                .pathsToExclude("/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi webApi() {
        return GroupedOpenApi.builder()
                .group("web")
                .pathsToMatch(
                        "/auth/**",
                        "/admin/**"
                )
                .pathsToExclude("/admin/reports/**")
                .build();
    }
}
