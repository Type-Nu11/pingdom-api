package com.typenull.pingdom.shared.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaceExplorationOpenApiConfig {

    @Bean
    public OpenApiCustomizer placeExplorationNullableReferenceCustomizer() {
        return openApi -> {
            replaceNullableReference(
                    openApi,
                    "PlaceVisitDecisionResponse",
                    "merchantInformation"
            );
            replaceNullableReference(
                    openApi,
                    "PlaceDetailResponse",
                    "merchantOwner"
            );
        };
    }

    private void replaceNullableReference(OpenAPI openApi, String schemaName, String propertyName) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Schema<?> ownerSchema = openApi.getComponents().getSchemas().get(schemaName);
        if (ownerSchema == null || ownerSchema.getProperties() == null) {
            return;
        }
        Schema<?> propertySchema = ownerSchema.getProperties().get(propertyName);
        if (propertySchema == null || propertySchema.get$ref() == null) {
            return;
        }

        ComposedSchema nullableReference = new ComposedSchema();
        nullableReference.addAllOfItem(new Schema<>().$ref(propertySchema.get$ref()));
        nullableReference.setNullable(true);
        nullableReference.setDescription(propertySchema.getDescription());
        ownerSchema.getProperties().put(propertyName, nullableReference);
    }
}
