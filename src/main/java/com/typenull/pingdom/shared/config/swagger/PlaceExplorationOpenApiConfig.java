package com.typenull.pingdom.shared.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 장소 응답의 선택적 객체 참조를 allOf와 nullable로 표현해 OpenAPI 3.0 문서에 null 계약을 남긴다. */
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

    /** 대상 schema·property·ref가 모두 있을 때만 감싼다. 해당 schema가 없는 다른 그룹 문서는 그대로 둔다. */
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
