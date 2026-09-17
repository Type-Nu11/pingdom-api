package com.typenull.pingdom.shared.config.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class VoiceAiOpenApiConfig {
    private static final String BASE = "/voice-ai/sessions";

    @Bean
    public GlobalOpenApiCustomizer voiceAiContractCustomizer() {
        return api -> {
            if (api.getPaths() == null || !api.getPaths().containsKey(BASE)) return;
            api.getComponents().addSchemas("ProviderEnvelopeV1", envelopeSchema());
            Operation send = api.getPaths().get(BASE + "/{sessionId}/messages").getPost();
            send.getResponses().put("200", new ApiResponse().description("ProviderEnvelope v1 최종 JSON")
                    .content(json(new Schema<>().$ref("#/components/schemas/ProviderEnvelopeV1"))));
            send.getResponses().put("400", new ApiResponse()
                    .description("VALIDATION_FAILED: text/requestId 검증 실패(errors 포함). INVALID_REQUEST_BODY: JSON 파싱 실패.")
                    .content(json(new ComposedSchema()
                            .addAnyOfItem(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                            .addAnyOfItem(new Schema<>().$ref("#/components/schemas/ValidationErrorResponse")))));
            error(send, "409", "REPLAY_CONFLICT: 동일 세션·requestId에 다른 text를 보냈습니다.");
            error(send, "502", "PROVIDER_UNAVAILABLE: provider timeout·일반 장애·비활성 설정. "
                    + "PROVIDER_RESPONSE_INVALID: schema 위반·16 KiB 초과. timeout 전용 code는 없습니다.");
            error(send, "429", "RATE_LIMIT_EXCEEDED: controller 진입 시 IP 기준 Redis 제한. "
                    + "consultations/intro와 공유하며 기본 1분 10회(설정 가능). replay도 제한에 포함됩니다. "
                    + "Retry-After는 제공하지 않습니다. 즉시 반복하지 말고 backoff 후 동일 requestId/text로 재시도합니다.");
            error(send, "503", "RATE_LIMIT_UNAVAILABLE: fail-open=false일 때 제한 저장소 장애. backoff 후 재시도합니다.");
            for (String path : List.of(BASE + "/{sessionId}/messages", BASE + "/{sessionId}/refresh", BASE + "/{sessionId}")) {
                api.getPaths().get(path).readOperations().forEach(operation -> {
                    error(operation, "404", "SESSION_NOT_FOUND: 존재하지 않는 세션");
                    error(operation, "403", "SESSION_FORBIDDEN: 다른 사용자 세션. ACCESS_DENIED: 공통 접근 거부");
                });
            }
            error(send, "410", "SESSION_EXPIRED: 만료 또는 종료된 세션. replay도 반환하지 않습니다.");
            error(api.getPaths().get(BASE + "/{sessionId}/refresh").getPost(), "410",
                    "SESSION_EXPIRED: 만료 또는 종료된 세션은 갱신할 수 없습니다.");
        };
    }

    private Schema<?> envelopeSchema() {
        try (InputStream stream = new ClassPathResource("openapi/provider-envelope.v1.schema.json").getInputStream()) {
            JsonNode source = Json.mapper().readTree(stream);
            // 앱의 고정 v1 계약을 OAS 3.0으로 변환한다. const는 단일 enum, 로컬 $defs는 인라인으로 표현한다.
            return Json.mapper().treeToValue(toOpenApi(source, source), Schema.class);
        } catch (IOException exception) {
            throw new IllegalStateException("ProviderEnvelope v1 계약을 읽을 수 없습니다.", exception);
        }
    }

    private JsonNode toOpenApi(JsonNode node, JsonNode root) {
        if (node.isArray()) {
            ArrayNode result = Json.mapper().createArrayNode();
            node.forEach(child -> result.add(toOpenApi(child, root)));
            return result;
        }
        if (!node.isObject()) return node.deepCopy();
        if (node.has("$ref")) return toOpenApi(root.at(node.get("$ref").asText().substring(1)), root);
        ObjectNode result = Json.mapper().createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (List.of("$schema", "$defs", "$comment").contains(key)) return;
            if (key.equals("const")) {
                result.putArray("enum").add(entry.getValue());
                result.put("type", entry.getValue().isIntegralNumber() ? "integer" : "string");
            } else result.set(key, toOpenApi(entry.getValue(), root));
        });
        if (result.has("enum") && !result.has("type")) result.put("type", "string");
        return result;
    }

    private void error(Operation operation, String status, String description) {
        operation.getResponses().put(status, new ApiResponse().description(description)
                .content(json(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
    }

    private Content json(Schema<?> schema) {
        return new Content().addMediaType("application/json", new MediaType().schema(schema));
    }
}
