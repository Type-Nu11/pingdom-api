package com.typenull.pingdom.shared.config.swagger;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.lang.reflect.Method;
import java.util.Map;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
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

    @Bean
    public GlobalOpenApiCustomizer authorizationContractCustomizer() {
        return this::applyAuthorizationContract;
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

    /**
     * SecurityConfig의 URL 인가 규칙을 OpenAPI에도 반영한다.
     * 공개 경로를 제외한 모든 API는 JWT가 필요하므로 개별 Controller의 누락으로
     * 인증 계약이 달라지지 않도록 401/403 공통 응답을 보완한다.
     */
    private void applyAuthorizationContract(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        ensureErrorResponseSchema(openApi);
        ensureValidationErrorResponseSchema(openApi);
        openApi.getPaths().forEach((path, pathItem) -> {
            pathItem.readOperations().forEach(operation -> {
                if (!isPublicPath(path)) {
                    if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                    }
                    ensureErrorResponse(operation, "401", authenticationFailureResponse());
                    ensureErrorResponse(operation, "403", accessDeniedResponse());
                }
                if (hasValidationInput(operation)) {
                    operation.getResponses().putIfAbsent("400", validationFailureResponse());
                }
            });
        });
    }

    private boolean isPublicPath(String path) {
        return "/".equals(path)
                || path.startsWith("/auth/")
                || "/consultations/intro".equals(path)
                || path.startsWith("/analysis/reports/");
    }

    private void ensureErrorResponseSchema(OpenAPI openApi) {
        ensureSchema(openApi, ErrorResponse.class);
    }

    private void ensureValidationErrorResponseSchema(OpenAPI openApi) {
        ensureSchema(openApi, ValidationErrorResponse.class);
    }

    private void ensureSchema(OpenAPI openApi, Class<?> type) {
        if (openApi.getComponents() == null) {
            return;
        }

        Map<String, Schema> schemas = ModelConverters.getInstance().read(type);
        schemas.forEach((name, schema) -> {
            if (openApi.getComponents().getSchemas() == null
                    || !openApi.getComponents().getSchemas().containsKey(name)) {
                openApi.getComponents().addSchemas(name, schema);
            }
        });
    }

    private boolean hasValidationInput(io.swagger.v3.oas.models.Operation operation) {
        if (operation.getRequestBody() != null) {
            return true;
        }
        if (operation.getParameters() == null) {
            return false;
        }

        return operation.getParameters().stream()
                .map(parameter -> parameter.getSchema())
                .filter(schema -> schema != null)
                .anyMatch(this::hasConstraints);
    }

    private boolean hasConstraints(Schema<?> schema) {
        return schema.getMinimum() != null
                || schema.getMaximum() != null
                || schema.getMinLength() != null
                || schema.getMaxLength() != null
                || schema.getPattern() != null;
    }

    private ApiResponse authenticationFailureResponse() {
        return errorResponse(
                "유효하지 않거나 만료된 Bearer JWT (INVALID_TOKEN 또는 EXPIRED_TOKEN)"
        );
    }

    private ApiResponse accessDeniedResponse() {
        return errorResponse("권한이 없거나 접근이 거부됨 (ACCESS_DENIED 또는 도메인 권한 오류)");
    }

    private ApiResponse validationFailureResponse() {
        return new ApiResponse()
                .description("요청 값 검증 실패 (VALIDATION_FAILED) 또는 도메인 입력 정책 위반")
                .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(new ComposedSchema()
                                .addOneOfItem(errorResponseSchema())
                                .addOneOfItem(new Schema<>().$ref("#/components/schemas/ValidationErrorResponse")))
                ));
    }

    private void ensureErrorResponse(
            io.swagger.v3.oas.models.Operation operation,
            String status,
            ApiResponse defaultResponse
    ) {
        ApiResponse response = operation.getResponses().get(status);
        if (response == null) {
            operation.getResponses().put(status, defaultResponse);
            return;
        }

        if (response.getDescription() == null || response.getDescription().isBlank()) {
            response.setDescription(defaultResponse.getDescription());
        }
        if (!referencesErrorResponse(response)) {
            if (response.getContent() == null || response.getContent().isEmpty()) {
                response.setContent(defaultResponse.getContent());
            } else {
                response.getContent().values().forEach(mediaType -> mediaType.setSchema(errorResponseSchema()));
            }
        }
    }

    private boolean referencesErrorResponse(ApiResponse response) {
        if (response.getContent() == null) {
            return false;
        }

        return response.getContent().values().stream()
                .map(MediaType::getSchema)
                .anyMatch(schema -> schema != null && "#/components/schemas/ErrorResponse".equals(schema.get$ref()));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(
                        "application/json",
                        new MediaType().schema(errorResponseSchema())
                ));
    }

    private Schema<?> errorResponseSchema() {
        return new Schema<>().$ref("#/components/schemas/ErrorResponse");
    }
}
