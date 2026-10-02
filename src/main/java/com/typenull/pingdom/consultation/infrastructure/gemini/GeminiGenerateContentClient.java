package com.typenull.pingdom.consultation.infrastructure.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typenull.pingdom.consultation.application.GeminiIntroClient;
import com.typenull.pingdom.consultation.application.GeminiVoiceClient;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini 첫 후보의 텍스트 조각을 안내 문구 또는 ProviderEnvelope JSON으로 읽는 HTTP 어댑터.
 * 안내 문구 누락은 빈 Optional, JSON 해석 실패는 null로 표현하고 전송 예외 및 최종 스키마 검증은 호출 서비스가 처리.
 */
@Component
@Slf4j
public class GeminiGenerateContentClient implements GeminiIntroClient, GeminiVoiceClient {

    private static final int MAX_OUTPUT_TOKENS = 80;
    private static final int CANDIDATE_COUNT = 1;
    private static final ObjectMapper ENVELOPE_MAPPER = new ObjectMapper();
    private static final JsonNode VOICE_RESPONSE_SCHEMA = loadVoiceResponseSchema();
    private static final String SYSTEM_INSTRUCTION = """
            사용자의 첫 상담 질문에 한국어로 짧고 공감하는 안내를 작성하세요.
            업종 카테고리를 선택하도록 자연스럽게 안내하고, 두 문장을 넘기지 마세요.
            질문에 포함되지 않은 개인정보나 이전 상담 내용은 추정하거나 언급하지 마세요.
            """;
    private static final String VOICE_SYSTEM_INSTRUCTION = """
            Return exactly one JSON object for Pingdom ProviderEnvelope v1. Never use markdown.
            Use numeric schemaVersion=1 and the supplied request id in id. The kind field must be
            assistant_message, clarification_request, command_request, or protocol_error.
            For assistant_message, put the reply in the top-level text field, never in an assistant_message field.
            Follow the supplied JSON schema exactly. Never emit command_result,
            source=app, credentials, location coordinates, reservation confirmation, or provider details.
            If a request is ambiguous, use clarification_request. If no safe command applies, use assistant_message.
            The envelope id must equal the supplied request id.
            Do not invent place or availability ids, dates, times, quantity, or location permission.
            Ask for missing command arguments with clarification_request. Write user-facing text in Korean.
            """;

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;

    public GeminiGenerateContentClient(RestClient geminiRestClient, GeminiProperties properties) {
        this.geminiRestClient = geminiRestClient;
        this.properties = properties;
    }

    @Override
    public Optional<String> generateIntro(String message) {
        JsonNode response = geminiRestClient.post()
                .uri("/models/{model}:generateContent", properties.model())
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", properties.apiKey())
                .body(new GenerateContentRequest(
                        List.of(new Content(List.of(new Part(message)))),
                        new Content(List.of(new Part(SYSTEM_INSTRUCTION))),
                        new GenerationConfig(MAX_OUTPUT_TOKENS, CANDIDATE_COUNT, null, null)
                ))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("candidates").isArray() || response.path("candidates").isEmpty()) {
            return Optional.empty();
        }

        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return Optional.empty();
        }

        String text = StreamSupport.stream(parts.spliterator(), false)
                .map(part -> part.path("text").asText())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return StringUtils.hasText(text) ? Optional.of(text) : Optional.empty();
    }

    @Override
    public JsonNode generateEnvelope(String message, String requestId) {
        try {
            return requestEnvelope(message, requestId);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String outcome = status == 401 || status == 403 ? "authentication"
                    : status == 429 ? "quota" : status == 504 ? "timeout" : "http_error";
            log.warn("Voice AI provider failure provider=gemini outcome={} httpStatus={}", outcome, status);
            throw exception;
        } catch (RestClientException exception) {
            String outcome = exception.getCause() instanceof java.net.SocketTimeoutException
                    ? "timeout" : "transport_error";
            log.warn("Voice AI provider failure provider=gemini outcome={} exceptionType={}",
                    outcome, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private JsonNode requestEnvelope(String message, String requestId) {
        JsonNode response = geminiRestClient.post()
                .uri("/models/{model}:generateContent", properties.model())
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", properties.apiKey())
                .body(new GenerateContentRequest(
                        List.of(new Content(List.of(new Part(message)))),
                        new Content(List.of(new Part(VOICE_SYSTEM_INSTRUCTION + "\nrequest id: " + requestId))),
                        new GenerationConfig(512, CANDIDATE_COUNT, MediaType.APPLICATION_JSON_VALUE,
                                voiceResponseSchema(requestId))
                ))
                .retrieve()
                .body(JsonNode.class);

        String text = extractText(response).orElse("");
        if (text.isBlank()) {
            log.warn("Voice AI provider failure provider=gemini outcome=invalid_response reason=missing_text");
            return null;
        }
        try {
            return ENVELOPE_MAPPER.readTree(text);
        } catch (IOException exception) {
            log.warn("Voice AI provider failure provider=gemini outcome=invalid_response reason=invalid_json");
            return null;
        }
    }

    private static JsonNode loadVoiceResponseSchema() {
        try (var input = GeminiGenerateContentClient.class.getResourceAsStream("/openapi/provider-envelope.v1.schema.json")) {
            if (input == null) throw new IllegalStateException("Voice AI response schema is missing");
            JsonNode source = ENVELOPE_MAPPER.readTree(input);
            return toGeminiSchema(source, source);
        } catch (IOException exception) {
            throw new IllegalStateException("Voice AI response schema cannot be read", exception);
        }
    }

    private static JsonNode toGeminiSchema(JsonNode node, JsonNode root) {
        if (node.isArray()) {
            ArrayNode result = ENVELOPE_MAPPER.createArrayNode();
            node.forEach(child -> result.add(toGeminiSchema(child, root)));
            return result;
        }
        if (!node.isObject()) return node.deepCopy();
        if (node.has("$ref")) return toGeminiSchema(root.at(node.path("$ref").asText().substring(1)), root);
        ObjectNode result = ENVELOPE_MAPPER.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            // 고정된 앱 계약을 재사용하되 공급자가 지원하지 않는 길이·pattern 검증은 runtime validator에 맡긴다.
            if (List.of("$schema", "$defs", "$comment", "minLength", "maxLength", "pattern").contains(key)) return;
            if ("const".equals(key)) {
                result.putArray("enum").add(entry.getValue());
                result.put("type", entry.getValue().isIntegralNumber() ? "integer" : "string");
            } else {
                result.set("oneOf".equals(key) ? "anyOf" : key, toGeminiSchema(entry.getValue(), root));
            }
        });
        if (result.has("enum") && !result.has("type")) result.put("type", "string");
        return result;
    }

    private JsonNode voiceResponseSchema(String requestId) {
        // 요청별 복사본에만 ID 제약을 적용해 동시 요청 간 schema 공유 변경을 방지한다.
        JsonNode schema = VOICE_RESPONSE_SCHEMA.deepCopy();
        for (JsonNode variant : schema.path("anyOf")) {
            ((ObjectNode) variant.path("properties").path("id")).putArray("enum").add(requestId);
        }
        return schema;
    }

    private Optional<String> extractText(JsonNode response) {
        if (response == null || !response.path("candidates").isArray() || response.path("candidates").isEmpty()) {
            return Optional.empty();
        }
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return Optional.empty();
        }
        String text = StreamSupport.stream(parts.spliterator(), false)
                .map(part -> part.path("text").asText())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + right)
                .orElse("");
        return StringUtils.hasText(text) ? Optional.of(text) : Optional.empty();
    }

    private record GenerateContentRequest(
            List<Content> contents,
            Content systemInstruction,
            GenerationConfig generationConfig
    ) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private record GenerationConfig(int maxOutputTokens, int candidateCount, String responseMimeType,
                                    JsonNode responseJsonSchema) {
    }
}
