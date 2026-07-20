package com.typenull.pingdom.shared.config.swagger;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "openapi-export"})
public class SpringdocGroupsConfig {

    @Bean
    public GroupedOpenApi appApi() {
        return GroupedOpenApi.builder()
                .group("app")
                .pathsToMatch(
                        "/users/**",
                        "/merchant-owner/**",
                        "/map/**",
                        "/places",
                        "/places/**",
                        "/events",
                        "/events/**",
                        "/offers",
                        "/offers/**",
                        "/coupons",
                        "/coupons/**",
                        "/place",
                        "/place/**",
                        "/notifications/**",
                        "/visitor-verification-reports",
                        "/visitor-verification-reports/**",
                        "/location-check-ins",
                        "/location-check-ins/**",
                        "/firebase/**"
                )
                .pathsToExclude("/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi webApi() {
        return GroupedOpenApi.builder()
                .group("web")
                .pathsToMatch(
                        "/admin/**"
                )
                .build();
    }

    @Bean
    public GroupedOpenApi commonApi() {
        return GroupedOpenApi.builder()
                .group("common")
                .pathsToMatch(
                        "/",
                        "/auth/**"
                )
                .build();
    }
}
