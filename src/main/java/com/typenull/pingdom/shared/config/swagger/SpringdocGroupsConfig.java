package com.typenull.pingdom.shared.config.swagger;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.reflect.Method;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"dev", "local", "openapi-export"})
public class SpringdocGroupsConfig {

    @Bean
    public GroupedOpenApi appApi(
            @Qualifier("placeExplorationNullableReferenceCustomizer")
            OpenApiCustomizer placeExplorationNullableReferenceCustomizer
    ) {
        return apiGroup("App")
                .addOpenApiCustomizer(placeExplorationNullableReferenceCustomizer)
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return apiGroup("Admin")
                .build();
    }

    @Bean
    public GroupedOpenApi merchantApi() {
        return apiGroup("Merchant")
                .build();
    }

    @Bean
    public GroupedOpenApi commonApi() {
        return apiGroup("Common")
                .build();
    }

    @Bean
    public GroupedOpenApi consultingApi() {
        return apiGroup("Consulting")
                .build();
    }

    private GroupedOpenApi.Builder apiGroup(String tagName) {
        return GroupedOpenApi.builder()
                .group(tagName.toLowerCase())
                .addOpenApiMethodFilter(method -> hasTag(method, tagName));
    }

    private boolean hasTag(Method method, String tagName) {
        Tag methodTag = method.getAnnotation(Tag.class);
        if (methodTag != null) {
            return tagName.equals(methodTag.name());
        }
        Tag tag = method.getDeclaringClass().getAnnotation(Tag.class);
        return tag != null && tagName.equals(tag.name());
    }
}
