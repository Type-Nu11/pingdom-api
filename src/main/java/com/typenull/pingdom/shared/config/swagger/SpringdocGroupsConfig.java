package com.typenull.pingdom.shared.config.swagger;

import com.typenull.pingdom.shared.api.dto.ErrorResponse;
import com.typenull.pingdom.shared.api.dto.ValidationErrorResponse;
import com.typenull.pingdom.shared.config.swagger.ApiAudience.Group;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringdocGroupsConfig {

    @Bean
    public GroupedOpenApi appApi(
            @Qualifier("placeExplorationNullableReferenceCustomizer")
            OpenApiCustomizer placeExplorationNullableReferenceCustomizer
    ) {
        return apiGroup(Group.APP)
                .addOpenApiCustomizer(placeExplorationNullableReferenceCustomizer)
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return apiGroup(Group.ADMIN)
                .build();
    }

    @Bean
    public GroupedOpenApi merchantApi() {
        return apiGroup(Group.MERCHANT)
                .build();
    }

    @Bean
    public GroupedOpenApi commonApi() {
        return apiGroup(Group.COMMON)
                .build();
    }

    @Bean
    public GroupedOpenApi consultingApi() {
        return apiGroup(Group.CONSULTING)
                .build();
    }

    @Bean
    public GlobalOpenApiCustomizer authorizationContractCustomizer() {
        return this::applyAuthorizationContract;
    }

    private GroupedOpenApi.Builder apiGroup(Group audience) {
        return GroupedOpenApi.builder()
                .group(audience.documentName())
                .addOpenApiMethodFilter(method -> Group.resolve(method) == audience)
                .addOpenApiCustomizer(api -> applyDisplayTags(api, audience));
    }

    @Bean
    public GlobalOperationCustomizer functionalTagCustomizer() {
        return (operation, handlerMethod) -> {
            if (Group.resolve(handlerMethod.getMethod()) == null) {
                return operation;
            }
            // Springdoc가 합친 클래스/메서드 태그 대신 가장 구체적인 분류 하나만 표시한다.
            Tag tag = handlerMethod.getMethodAnnotation(Tag.class);
            if (tag == null) {
                tag = handlerMethod.getBeanType().getAnnotation(Tag.class);
            }
            if (tag == null || tag.name().isBlank()) {
                throw new IllegalStateException("기능 분류가 없는 API: " + handlerMethod);
            }
            operation.setTags(List.of(tag.name()));
            return operation;
        };
    }

    @Bean
    public GlobalOpenApiCustomizer functionalTagDescriptionsCustomizer() {
        return api -> applyDisplayTags(api, null);
    }

    private void applyDisplayTags(OpenAPI api, Group audience) {
        if (api.getPaths() == null) {
            return;
        }
        Set<String> used = new LinkedHashSet<>();
        api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            if (operation.getTags() != null) {
                used.addAll(operation.getTags());
            }
        }));
        Map<String, io.swagger.v3.oas.models.tags.Tag> tags = new LinkedHashMap<>();
        List<SwaggerTagCatalog.Section> sections = audience == null
                ? SwaggerTagCatalog.SECTIONS : SwaggerTagCatalog.sections(audience);
        sections.stream().filter(section -> used.contains(section.name())).forEach(section -> {
            io.swagger.v3.oas.models.tags.Tag tag = tags.computeIfAbsent(section.name(),
                    name -> new io.swagger.v3.oas.models.tags.Tag().name(name));
            // 전체 문서에서는 여러 소속이 공유하는 기능명의 설명을 함께 보존한다.
            tag.setDescription(tag.getDescription() == null ? section.description()
                    : tag.getDescription() + " / " + section.description());
        });
        if (api.getTags() != null) {
            api.getTags().stream().filter(tag -> used.contains(tag.getName()))
                    .forEach(tag -> tags.putIfAbsent(tag.getName(), tag));
        }
        api.setTags(List.copyOf(tags.values()));
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
                || "/consultations/intro".equals(path);
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
